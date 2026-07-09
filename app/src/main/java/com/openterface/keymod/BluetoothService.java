package com.openterface.keymod;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.hid.Ch9329InboundParser;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleAlreadyConnectedException;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final String LOG_PREFIX = "[Bluetooth] ";
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final long RECONNECT_DELAY_MS = 5000; // Reconnect delay: 5 seconds
    private static final long RSSI_POLL_INTERVAL_MS = 2000;

    private final IBinder binder = new BluetoothBinder();
    private RxBleClient rxBleClient;
    private RxBleConnection activeConnection;
    private final CompositeDisposable connectionDisposables = new CompositeDisposable();
    private final Set<String> connectingDevices = new HashSet<>();
    private RxBleDevice connectedDevice;
    private Disposable reconnectDisposable;
    private Disposable rssiPollDisposable;
    private final Set<ConnectionStateListener> connectionStateListeners = new CopyOnWriteArraySet<>();
    private volatile boolean reconnectSuppressed;
    private int alreadyConnectedRetryCount = 0;
    private static final int MAX_ALREADY_CONNECTED_RETRIES = 3;
    private static final long ALREADY_CONNECTED_RETRY_DELAY_MS = 1000;
    @Nullable
    private Ch9329InboundParser hostLockInboundParser;
    @Nullable
    private Disposable hostLockNotifyDisposable;

    // ── BLE-Eth tunnel callbacks (shared characteristic FFF1) ─────────
    public interface BleEthDataCallback {
        void onBleEthData(byte[] data);
    }
    private final Set<BleEthDataCallback> bleEthCallbacks = new CopyOnWriteArraySet<>();
    private Disposable bleEthNotifyDisposable;

    private static final int BLE_ETH_TARGET_MTU = 247;
    private static final int BLE_ETH_WRITE_TIMEOUT_MS = 5000;
    private static final int BLE_ETH_INTER_CHUNK_DELAY_MS = 10;
    private static final int SAFE_BLE_STREAM_CHUNK = 128;
    private static final int BLE_ETH_FRAME_CMD_INDEX = 3;
    private static final int BLE_ETH_CMD_CONNECT = 0x10;
    private static final int BLE_ETH_CMD_DISCONNECT = 0x12;
    private static final int BLE_ETH_CMD_INFO = 0x1F;

    private final ExecutorService bleEthWriteExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BleEth-WriteExecutor");
        thread.setDaemon(true);
        return thread;
    });
    @Nullable
    private BluetoothGattCharacteristic bleEthWriteCharacteristic;

    /** Register for BLE-Eth tunnel data notifications. */
    public void addBleEthCallback(BleEthDataCallback callback) {
        if (callback != null) {
            bleEthCallbacks.add(callback);
            Log.i(TAG, LOG_PREFIX + "BLE-Eth callback registered. Total callbacks: " + bleEthCallbacks.size());
            // Note: We do NOT create a separate notification subscription here.
            // The host lock notification (startHostLockBleNotifications) already subscribes
            // to FFF1 and dispatches to BLE-Eth callbacks. Creating a second subscription
            // would cause duplicate delivery and corrupt frame parsing for split frames.
            if (hostLockNotifyDisposable != null) {
                Log.v(TAG, LOG_PREFIX + "BLE-Eth callbacks will receive data via shared host lock subscription");
            } else {
                Log.w(TAG, LOG_PREFIX + "No active BLE notification subscription yet - callbacks will not receive data until connected");
            }
        }
    }

    /** Unregister a BLE-Eth data callback. */
    public void removeBleEthCallback(BleEthDataCallback callback) {
        if (callback != null) {
            bleEthCallbacks.remove(callback);
            Log.v(TAG, LOG_PREFIX + "BLE-Eth callback unregistered. Remaining callbacks: " + bleEthCallbacks.size());
        }
        if (bleEthCallbacks.isEmpty() && bleEthNotifyDisposable != null) {
            Log.i(TAG, LOG_PREFIX + "No more BLE-Eth callbacks, stopping notifications");
            stopBleEthNotifications();
        }
    }

    private void startBleEthNotifications(RxBleConnection connection) {
        if (bleEthNotifyDisposable != null) {
            Log.w(TAG, LOG_PREFIX + "BLE-Eth notifications already active, skipping setup");
            return;
        }

        Log.i(TAG, LOG_PREFIX + "Starting BLE-Eth notification subscription for characteristic " + NOTIFY_CHARACTERISTIC_UUID);

        bleEthNotifyDisposable = connection.setupNotification(NOTIFY_CHARACTERISTIC_UUID)
            .flatMap(notificationObservable -> {
                Log.v(TAG, LOG_PREFIX + "BLE-Eth notification setup successful, subscribing to observable");
                return notificationObservable;
            })
            .subscribe(
                bytes -> {
                    Log.v(TAG, LOG_PREFIX + "BLE-Eth notification received: " + bytes.length + " bytes: " + bytesToHex(bytes));
                    int callbackCount = bleEthCallbacks.size();
                    Log.v(TAG, LOG_PREFIX + "BLE-Eth dispatching " + bytes.length + " bytes to " + callbackCount + " callbacks");
                    for (BleEthDataCallback callback : bleEthCallbacks) {
                        try {
                            callback.onBleEthData(bytes);
                        } catch (Exception e) {
                            Log.e(TAG, LOG_PREFIX + "BLE-Eth callback threw exception: " + e.getMessage(), e);
                        }
                    }
                },
                error -> {
                    Log.e(TAG, LOG_PREFIX + "BLE-Eth notification error: " + error.getClass().getSimpleName() + " - " + error.getMessage(), error);
                    bleEthNotifyDisposable = null;
                },
                () -> {
                    Log.w(TAG, LOG_PREFIX + "BLE-Eth notifications completed (unexpected)");
                    bleEthNotifyDisposable = null;
                }
            );

        connectionDisposables.add(bleEthNotifyDisposable);
        Log.i(TAG, LOG_PREFIX + "BLE-Eth notification subscription added to connectionDisposables");
    }

    private void stopBleEthNotifications() {
        if (bleEthNotifyDisposable != null) {
            Log.v(TAG, LOG_PREFIX + "Stopping BLE-Eth notifications");
            bleEthNotifyDisposable.dispose();
            connectionDisposables.remove(bleEthNotifyDisposable);
            bleEthNotifyDisposable = null;
            Log.i(TAG, LOG_PREFIX + "BLE-Eth notifications stopped");
        } else {
            Log.v(TAG, LOG_PREFIX + "stopBleEthNotifications called but no active subscription");
        }
    }

    /** Write raw data to the BLE characteristic. Control frames use acknowledged writes. */
    public void writeBleEthData(byte[] data) {
        if (activeConnection == null) {
            Log.w(TAG, LOG_PREFIX + "Cannot write BLE-Eth data: no active connection");
            return;
        }
        Log.v(TAG, LOG_PREFIX + "BLE-Eth TX " + data.length + " bytes: " + bytesToHex(data));

        byte[] frame = Arrays.copyOf(data, data.length);
        bleEthWriteExecutor.execute(() -> writeBleEthInternal(frame));
    }

    private void writeBleEthInternal(byte[] data) {
        RxBleConnection connection = activeConnection;
        if (connection == null) {
            Log.w(TAG, LOG_PREFIX + "Skipping BLE-Eth write after disconnect");
            return;
        }

        BluetoothGattCharacteristic characteristic = getBleEthWriteCharacteristic(connection);
        if (characteristic == null) {
            return;
        }

        boolean useAcknowledgedWrite = isControlFrame(data);

        if (data.length <= SAFE_BLE_STREAM_CHUNK) {
            writeBleEthChunk(connection, characteristic, data, useAcknowledgedWrite);
            return;
        }

        writeBleEthFragmented(connection, characteristic, data);
    }

    private void writeBleEthFragmented(RxBleConnection connection,
                                       BluetoothGattCharacteristic characteristic,
                                       byte[] data) {
        try {
            int offset = 0;
            while (offset < data.length) {
                int chunkLen = Math.min(SAFE_BLE_STREAM_CHUNK, data.length - offset);
                byte[] chunk = new byte[chunkLen];
                System.arraycopy(data, offset, chunk, 0, chunkLen);
                offset += chunkLen;
                boolean lastChunk = offset >= data.length;
                Log.v(TAG, LOG_PREFIX + "BLE-Eth TX chunk: " + chunk.length + " bytes"
                        + (lastChunk ? " (last)" : ""));

                if (!writeBleEthChunk(connection, characteristic, chunk, false)) {
                    return;
                }

                if (!lastChunk) {
                    Thread.sleep(BLE_ETH_INTER_CHUNK_DELAY_MS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, LOG_PREFIX + "BLE-Eth fragmented write interrupted");
        }
    }

    @Nullable
    private BluetoothGattCharacteristic getBleEthWriteCharacteristic(RxBleConnection connection) {
        if (bleEthWriteCharacteristic != null) {
            return bleEthWriteCharacteristic;
        }

        CountDownLatch characteristicLatch = new CountDownLatch(1);
        final BluetoothGattCharacteristic[] characteristicHolder = new BluetoothGattCharacteristic[1];

        connection.discoverServices()
                .flatMap(services -> services.getCharacteristic(WRITE_CHARACTERISTIC_UUID))
                .subscribe(
                        characteristic -> {
                            bleEthWriteCharacteristic = characteristic;
                            characteristicHolder[0] = characteristic;
                            characteristicLatch.countDown();
                        },
                        throwable -> {
                            Log.e(TAG, LOG_PREFIX + "BLE-Eth get char error: " + throwable);
                            characteristicLatch.countDown();
                        }
                );

        try {
            if (!characteristicLatch.await(BLE_ETH_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, LOG_PREFIX + "BLE-Eth get characteristic timed out");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, LOG_PREFIX + "BLE-Eth get characteristic interrupted");
            return null;
        }

        if (characteristicHolder[0] == null) {
            Log.e(TAG, LOG_PREFIX + "BLE-Eth write characteristic unavailable");
        }
        return characteristicHolder[0];
    }

    private boolean writeBleEthChunk(RxBleConnection connection,
                                     BluetoothGattCharacteristic characteristic,
                                     byte[] data,
                                     boolean acknowledgedWrite) {
        characteristic.setWriteType(acknowledgedWrite
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        CountDownLatch writeLatch = new CountDownLatch(1);
        final boolean[] writeSucceeded = new boolean[1];

        connection.writeCharacteristic(characteristic, data)
                .subscribe(
                        writtenBytes -> {
                            writeSucceeded[0] = true;
                            writeLatch.countDown();
                        },
                        throwable -> {
                            Log.e(TAG, LOG_PREFIX + "BLE-Eth write error: " + throwable);
                            writeLatch.countDown();
                        }
                );

        try {
            if (!writeLatch.await(BLE_ETH_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, LOG_PREFIX + "BLE-Eth write timed out");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, LOG_PREFIX + "BLE-Eth write interrupted");
            return false;
        }

        return writeSucceeded[0];
    }

    private boolean isControlFrame(byte[] data) {
        if (data == null || data.length <= BLE_ETH_FRAME_CMD_INDEX) {
            return false;
        }
        int cmd = data[BLE_ETH_FRAME_CMD_INDEX] & 0xFF;
        return cmd == BLE_ETH_CMD_CONNECT
                || cmd == BLE_ETH_CMD_DISCONNECT
                || cmd == BLE_ETH_CMD_INFO;
    }

    public interface ConnectionStateListener {
        void onBluetoothConnecting(RxBleDevice device);
        void onBluetoothConnected(RxBleDevice device);
        void onBluetoothDisconnected(RxBleDevice device);
        void onBluetoothError(RxBleDevice device, String error);
        void onBluetoothRssiChanged(RxBleDevice device, int rssi);
    }

    public class BluetoothBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    /**
     * When true, {@link #scheduleReconnect} is a no-op (used during BluetoothAutoConnectManager
     * multi-device attempts to avoid racing with its retry loop).
     */
    public void setReconnectSuppressed(boolean suppressed) {
        this.reconnectSuppressed = suppressed;
    }

    /** Shared with USB serial; inbound notify bytes are parsed for host keyboard LED state. */
    public void setHostLockInboundParser(@Nullable Ch9329InboundParser parser) {
        hostLockInboundParser = parser;
        RxBleConnection connection = activeConnection;
        if (parser != null && connection != null) {
            startHostLockBleNotifications(connection);
        }
    }

    /**
     * Set a listener for USB mode status responses (CMD 0xB0) on the BLE inbound parser.
     * If the parser already exists and a connection is active, the listener is attached immediately.
     */
    public void setUsbModeResponseListener(@Nullable Ch9329InboundParser.UsbModeResponseListener listener) {
        Ch9329InboundParser parser = hostLockInboundParser;
        if (parser != null) {
            parser.setUsbModeResponseListener(listener);
        }
    }

    private void startHostLockBleNotifications(@NonNull RxBleConnection connection) {
        stopHostLockBleNotifications();
        // Always set up the shared notification subscription on BLE connect.
        // It dispatches to BOTH the host lock parser AND BLE-Eth callbacks.
        // Without this, BLE-Eth won't receive any responses from the firmware.
        hostLockNotifyDisposable =
                connection.setupNotification(NOTIFY_CHARACTERISTIC_UUID)
                        .flatMap(obs -> obs)
                        .subscribe(
                                bytes -> {
                                    if (bytes == null || bytes.length == 0) return;
                                    Log.v(TAG, LOG_PREFIX + "BLE RX " + bytes.length + " bytes: " + bytesToHex(bytes));
                                    // 1) Dispatch to host lock inbound parser (existing use)
                                    if (hostLockInboundParser != null) {
                                        hostLockInboundParser.append(bytes, bytes.length);
                                    }
                                    // 2) Dispatch ALL notifications to BLE-Eth callbacks.
                                    //    The BLE-Eth frame parser (BleEthTransport) accumulates
                                    //    partial bytes and only emits frames when a complete
                                    //    57 AB header + payload + checksum is received.
                                    //    Filtering here would drop continuation bytes and break
                                    //    multi-notification frames.
                                    if (!bleEthCallbacks.isEmpty()) {
                                        Log.v(TAG, LOG_PREFIX + "BLE-Eth dispatching " + bytes.length + " bytes to " + bleEthCallbacks.size() + " callbacks");
                                        for (BleEthDataCallback cb : bleEthCallbacks) {
                                            cb.onBleEthData(bytes);
                                        }
                                    }
                                },
                                throwable ->
                                        Log.w(
                                                TAG,
                                                LOG_PREFIX
                                                        + "Shared notify subscription error: "
                                                        + throwable));
        if (hostLockNotifyDisposable != null) {
            connectionDisposables.add(hostLockNotifyDisposable);
        }
    }

    private void stopHostLockBleNotifications() {
        if (hostLockNotifyDisposable != null && !hostLockNotifyDisposable.isDisposed()) {
            hostLockNotifyDisposable.dispose();
            connectionDisposables.remove(hostLockNotifyDisposable);
        }
        hostLockNotifyDisposable = null;
    }

    public void addConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            connectionStateListeners.add(listener);
        }
    }

    public void removeConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            connectionStateListeners.remove(listener);
        }
    }

    private void notifyBluetoothConnecting(RxBleDevice device) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            listener.onBluetoothConnecting(device);
        }
    }

    private void notifyBluetoothConnected(RxBleDevice device) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            listener.onBluetoothConnected(device);
        }
    }

    private void notifyBluetoothDisconnected(RxBleDevice device) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            listener.onBluetoothDisconnected(device);
        }
    }

    private void notifyBluetoothError(RxBleDevice device, String error) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            listener.onBluetoothError(device, error);
        }
    }

    private void notifyBluetoothRssiChanged(RxBleDevice device, int rssi) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            listener.onBluetoothRssiChanged(device, rssi);
        }
    }

    private void startRssiPolling() {
        stopRssiPolling();
        rssiPollDisposable =
                Observable.interval(0, RSSI_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
                        .subscribe(
                                tick -> {
                                    RxBleConnection connection = activeConnection;
                                    RxBleDevice device = connectedDevice;
                                    if (connection == null || device == null) {
                                        return;
                                    }
                                    connection.readRssi()
                                            .subscribe(
                                                    rssi -> notifyBluetoothRssiChanged(device, rssi),
                                                    throwable ->
                                                            Log.w(
                                                                    TAG,
                                                                    LOG_PREFIX
                                                                            + "RSSI read failed: "
                                                                            + throwable));
                                },
                                throwable ->
                                        Log.w(
                                                TAG,
                                                LOG_PREFIX + "RSSI polling stopped: " + throwable));
        connectionDisposables.add(rssiPollDisposable);
    }

    private void stopRssiPolling() {
        if (rssiPollDisposable != null && !rssiPollDisposable.isDisposed()) {
            rssiPollDisposable.dispose();
            connectionDisposables.remove(rssiPollDisposable);
        }
        rssiPollDisposable = null;
    }

    public void connectToDevice(RxBleDevice device) {
        if (rxBleClient == null) {
            Log.e(TAG, LOG_PREFIX + "RxBleClient is not initialized");
            return;
        }

        String deviceAddress = device.getMacAddress();

        // Check if already connected to this device
        if (isConnected() && connectedDevice != null &&
                connectedDevice.getMacAddress().equals(deviceAddress)) {
            Log.v(TAG, LOG_PREFIX + "Already connected to device: " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + ")");
            return;
        }

        // Tear down the previous connection before starting a new one.
        // Without this, the old GATT connection lingers in connectionDisposables
        // alongside the new one.  Android's BLE stack typically allows only one
        // active GATT connection, so the old one causes BleDisconnectedException
        // on the new attempt.
        if (connectedDevice != null
                && !connectedDevice.getMacAddress().equals(deviceAddress)) {
            Log.v(TAG, LOG_PREFIX + "Switching device from "
                    + sanitizeDeviceName(connectedDevice.getName())
                    + " (" + connectedDevice.getMacAddress() + ") to "
                    + sanitizeDeviceName(device.getName())
                    + " (" + deviceAddress + ")");
            teardownCurrentConnection();
        }

        synchronized (connectingDevices) {
            if (connectingDevices.contains(deviceAddress)) {
                Log.v(TAG, LOG_PREFIX + "Already connecting to device: " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + ")");
                return;
            }
            connectingDevices.add(deviceAddress);
        }

        stopReconnect(); // Clear previous reconnect attempts
        connectedDevice = device;
        notifyBluetoothConnecting(device);

        Disposable connectionDisposable = device.establishConnection(false)
                .doOnDispose(() -> {
                    synchronized (connectingDevices) {
                        connectingDevices.remove(deviceAddress);
                    }
                    Log.v(TAG, LOG_PREFIX + "Connection disposed for device: " + sanitizeDeviceName(device.getName()));
                })
                .subscribe(
                        connection -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            // Guard: if the user switched to another device while
                            // this connection was being established, ignore the result.
                            if (connectedDevice == null
                                    || !connectedDevice.getMacAddress().equals(deviceAddress)) {
                                Log.v(TAG, LOG_PREFIX + "Stale connection callback for "
                                        + deviceAddress + ", current device is "
                                        + (connectedDevice != null ? connectedDevice.getMacAddress() : "null"));
                                return;
                            }
                            activeConnection = connection;
                            bleEthWriteCharacteristic = null;
                            alreadyConnectedRetryCount = 0;
                            Log.v(TAG, LOG_PREFIX + "Connected to " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + ")");
                            notifyBluetoothConnected(device);
                            startRssiPolling();
                            connection.requestMtu(BLE_ETH_TARGET_MTU)
                                    .subscribe(
                                            mtu -> {
                                                Log.i(TAG, LOG_PREFIX + "BLE MTU negotiated: " + mtu);
                                                startHostLockBleNotifications(connection);
                                            },
                                            throwable -> {
                                                Log.w(TAG, LOG_PREFIX + "BLE MTU request failed, continuing: " + throwable);
                                                startHostLockBleNotifications(connection);
                                            }
                                    );
                        },
                        throwable -> {
                            synchronized (connectingDevices) {
                                connectingDevices.remove(deviceAddress);
                            }
                            // Guard: if the user switched to another device, do not
                            // update state or schedule reconnect for the old device.
                            if (connectedDevice != null
                                    && !connectedDevice.getMacAddress().equals(deviceAddress)) {
                                Log.v(TAG, LOG_PREFIX + "Stale error callback for "
                                        + deviceAddress + ", current device is "
                                        + connectedDevice.getMacAddress() + " - ignoring");
                                return;
                            }
                            Log.e(TAG, LOG_PREFIX + "Connection error for device " + sanitizeDeviceName(device.getName()) + " (" + deviceAddress + "): " + throwable.toString());
                            activeConnection = null;
                            bleEthWriteCharacteristic = null;
                            stopHostLockBleNotifications();
                            notifyBluetoothError(device, throwable.toString());
                            notifyBluetoothDisconnected(device);
                            stopRssiPolling();
                            // BleAlreadyConnectedException: the previous GATT connection on this
                            // device hasn't fully released at the OS level yet.  Retry after a
                            // short delay to give Android's BLE stack time to clean up.
                            if (throwable instanceof BleAlreadyConnectedException) {
                                if (alreadyConnectedRetryCount < MAX_ALREADY_CONNECTED_RETRIES) {
                                    alreadyConnectedRetryCount++;
                                    Log.w(TAG, LOG_PREFIX + "Device already connected at GATT level, "
                                            + "retry " + alreadyConnectedRetryCount
                                            + "/" + MAX_ALREADY_CONNECTED_RETRIES
                                            + " after " + ALREADY_CONNECTED_RETRY_DELAY_MS + "ms");
                                    Observable.timer(ALREADY_CONNECTED_RETRY_DELAY_MS,
                                            TimeUnit.MILLISECONDS)
                                            .subscribe(
                                                    ignored -> {
                                                        if (connectedDevice != null
                                                                && connectedDevice.getMacAddress()
                                                                        .equals(deviceAddress)) {
                                                            connectToDevice(device);
                                                        }
                                                    },
                                                    err -> Log.e(TAG, LOG_PREFIX
                                                            + "Already-connected retry error: "
                                                            + err));
                                    return;
                                }
                                Log.e(TAG, LOG_PREFIX
                                        + "BleAlreadyConnectedException persisted after "
                                        + MAX_ALREADY_CONNECTED_RETRIES + " retries");
                                alreadyConnectedRetryCount = 0;
                            }
                            // Check if the error is a BleDisconnectedException with status 255
                            if (throwable instanceof BleDisconnectedException) {
                                String errorMessage = throwable.toString();
                                // Parse status from message (e.g., "with status 255")
                                Pattern pattern = Pattern.compile("with status (\\d+)");
                                Matcher matcher = pattern.matcher(errorMessage);
                                if (matcher.find()) {
                                    int status = Integer.parseInt(matcher.group(1));
                                    if (status == 255) {
                                        Log.w(TAG, LOG_PREFIX + "Skipping reconnect due to GATT_OUT_OF_RANGE error (status 255)");
                                        return;
                                    }
                                }
                            }
                            if (!reconnectSuppressed) {
                                scheduleReconnect(device);
                            }
                        }
                );
        connectionDisposables.add(connectionDisposable);
    }

    private void scheduleReconnect(RxBleDevice device) {
        if (reconnectSuppressed) {
            return;
        }
        stopReconnect();
        reconnectDisposable = Observable.timer(RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
                .subscribe(
                        aLong -> {
                            if (connectedDevice != null && connectedDevice.getMacAddress().equals(device.getMacAddress())) {
                                Log.v(TAG, LOG_PREFIX + "Attempting to reconnect to " + sanitizeDeviceName(device.getName()));
                                connectToDevice(device);
                            }
                        },
                        throwable -> Log.e(TAG, LOG_PREFIX + "Reconnect scheduling error: " + throwable.toString())
                );
        connectionDisposables.add(reconnectDisposable);
    }

    private void stopReconnect() {
        if (reconnectDisposable != null && !reconnectDisposable.isDisposed()) {
            reconnectDisposable.dispose();
            connectionDisposables.remove(reconnectDisposable);
        }
    }

    @SuppressLint("CheckResult")
    public void     sendData(byte[] keyBoardData) {
        String packetHex = bytesToHex(keyBoardData);
        if (activeConnection == null) {
            Log.w(TAG, LOG_PREFIX + "Cannot send data: No active connection, packet=" + packetHex);
            if (connectedDevice != null) {
                Log.v(TAG, LOG_PREFIX + "Attempting to reconnect before sending data");
                connectToDevice(connectedDevice);
            }
            return;
        }

        Log.i(TAG, LOG_PREFIX + "BLE write request packet=" + packetHex);

        activeConnection.discoverServices()
                .flatMap(services -> services.getCharacteristic(WRITE_CHARACTERISTIC_UUID))
                .subscribe(
                        characteristic -> {
                            int properties = characteristic.getProperties();
                            boolean supportsWrite = (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
                            if (supportsWrite) {
//                                byte[] dataBytes = CustomKeyboardView.hexStringToByteArray(keyBoardData);
                                activeConnection.writeCharacteristic(WRITE_CHARACTERISTIC_UUID, keyBoardData)
                                        .subscribe(
                                                writtenBytes -> Log.i(TAG, LOG_PREFIX + "BLE write success packet=" + packetHex),
                                                throwable -> Log.e(TAG, LOG_PREFIX + "Write error packet=" + packetHex + " error=" + throwable.toString())
                                        );
                            } else {
                                Log.w(TAG, LOG_PREFIX + "Characteristic " + WRITE_CHARACTERISTIC_UUID + " does not support write operations");
                            }
                        },
                        throwable -> Log.e(TAG, LOG_PREFIX + "Error retrieving characteristic for packet=" + packetHex + ": " + throwable.toString())
                );
    }

    private String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public RxBleDevice getConnectedDevice() {
        return connectedDevice;
    }

    public boolean isConnected() {
        return activeConnection != null;
    }

    private String sanitizeDeviceName(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connectionDisposables.dispose();
        bleEthWriteExecutor.shutdownNow();
        stopRssiPolling();
        if (connectedDevice != null) {
            notifyBluetoothDisconnected(connectedDevice);
        }
        activeConnection = null;
        bleEthWriteCharacteristic = null;
        connectedDevice = null;
        stopReconnect();
        Log.v(TAG, LOG_PREFIX + "BluetoothService destroyed");
    }

    public void disconnect() {
        if (activeConnection != null) {
            RxBleDevice previousDevice = connectedDevice;
            stopHostLockBleNotifications();
            connectionDisposables.clear();
            stopRssiPolling();
            activeConnection = null;
            bleEthWriteCharacteristic = null;
            connectedDevice = null;
            if (previousDevice != null) {
                notifyBluetoothDisconnected(previousDevice);
            }
            Log.v(TAG, LOG_PREFIX + "Bluetooth disconnected");
        }
    }

    /**
     * Tear down the current connection without notifying listeners.
     * Used internally by {@link #connectToDevice} when switching between devices,
     * so the old GATT connection is fully cleaned up before the new one starts.
     * Listeners are not notified because the caller will immediately emit
     * onBluetoothConnecting for the new device.
     */
    private void teardownCurrentConnection() {
        stopHostLockBleNotifications();
        stopBleEthNotifications();
        stopRssiPolling();
        stopReconnect();
        connectionDisposables.clear();
        activeConnection = null;
        bleEthWriteCharacteristic = null;
        connectedDevice = null;
        synchronized (connectingDevices) {
            connectingDevices.clear();
        }
    }
}