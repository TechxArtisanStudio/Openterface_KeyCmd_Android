package com.openterface.keymod;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

/**
 * Automatic Bluetooth connection on app launch: tries paired devices in last-used order via
 * direct MAC connection, then optionally a short scan only when exactly one in-range device
 * matches a known paired MAC.
 */
public class BluetoothAutoConnectManager {
    private static final String TAG = "BTAutoConnect";
    private static final int MAX_ROUNDS = 3;
    private static final int SCAN_DURATION_MS = 5000;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int PER_DEVICE_TIMEOUT_MS = 12000;

    private final Context context;
    private final RxBleClient rxBleClient;
    private final BluetoothService bluetoothService;
    private final ConnectionManager connectionManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Disposable scanDisposable;
    private int currentRound = 0;
    private boolean isConnecting = false;
    private AutoConnectListener listener;
    private BluetoothService.ConnectionStateListener attemptListener;
    private Runnable attemptTimeoutRunnable;
    private int attemptPairedIndex = -1;
    private final AtomicBoolean attemptOutcomeHandled = new AtomicBoolean(false);

    public interface AutoConnectListener {
        void onAutoConnectStarted();

        void onAutoConnectSuccess(RxBleDevice device);

        void onAutoConnectFailed(String reason);

        void onAutoConnectRetrying(int retryCount);
    }

    public BluetoothAutoConnectManager(
            Context context,
            RxBleClient rxBleClient,
            BluetoothService bluetoothService,
            ConnectionManager connectionManager) {
        this.context = context.getApplicationContext();
        this.rxBleClient = rxBleClient;
        this.bluetoothService = bluetoothService;
        this.connectionManager = connectionManager;
    }

    public void setAutoConnectListener(AutoConnectListener listener) {
        this.listener = listener;
    }

    public void startAutoConnect() {
        if (isConnecting) {
            Log.v(TAG, "Auto-connect already in progress");
            return;
        }
        if (connectionManager != null && !connectionManager.isAutoConnectEnabled()) {
            Log.v(TAG, "Auto-connect disabled in settings");
            return;
        }
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            notifyFailed("Bluetooth not supported");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            notifyFailed("Bluetooth is disabled");
            return;
        }
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions");
            notifyFailed("Missing Bluetooth permissions");
            return;
        }
        if (bluetoothService != null && bluetoothService.isConnected()) {
            Log.v(TAG, "Already connected to a Bluetooth device");
            RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
            if (connectedDevice != null) {
                notifySuccess(connectedDevice);
            }
            return;
        }
        if (connectionManager == null) {
            Log.e(TAG, "ConnectionManager is null");
            notifyFailed("ConnectionManager not ready");
            return;
        }

        currentRound = 0;
        isConnecting = true;
        if (listener != null) {
            listener.onAutoConnectStarted();
        }
        bluetoothService.setReconnectSuppressed(true);
        beginRound();
    }

    private void beginRound() {
        if (!isConnecting) {
            return;
        }
        List<PairedBleDevice> paired = connectionManager != null ? connectionManager.getPairedDevicesByRecency() : new ArrayList<>();
        if (paired.isEmpty()) {
            String lastMac = connectionManager != null ? connectionManager.getLastBleDeviceMac() : null;
            if (lastMac != null && !lastMac.isEmpty()) {
                trySingleMacThenFallback(lastMac);
                return;
            }
            tryScanFallbackOnly();
            return;
        }
        attemptPairedIndex = 0;
        tryPairedAtIndex(attemptPairedIndex);
    }

    private void trySingleMacThenFallback(String mac) {
        attemptPairedIndex = -1;
        startAttemptForMac(mac, () -> tryScanFallbackOnly());
    }

    private void tryPairedAtIndex(int index) {
        if (!isConnecting) {
            return;
        }
        List<PairedBleDevice> paired = connectionManager.getPairedDevicesByRecency();
        if (index >= paired.size()) {
            tryScanFallbackOnly();
            return;
        }
        PairedBleDevice p = paired.get(index);
        attemptPairedIndex = index;
        startAttemptForMac(
                p.mac,
                () -> {
                    if (!isConnecting) {
                        return;
                    }
                    tryPairedAtIndex(index + 1);
                });
    }

    private void startAttemptForMac(String mac, Runnable onFailureNext) {
        if (!isConnecting || bluetoothService == null) {
            return;
        }
        attemptOutcomeHandled.set(false);
        unregisterAttemptListener();
        cancelAttemptTimeout();
        final String expectedMac = mac.toUpperCase(Locale.US);
        RxBleDevice device = rxBleClient.getBleDevice(expectedMac);
        attemptListener =
                new BluetoothService.ConnectionStateListener() {
                    @Override
                    public void onBluetoothConnecting(RxBleDevice d) {}

                    @Override
                    public void onBluetoothConnected(RxBleDevice d) {
                        if (!isConnecting) {
                            return;
                        }
                        if (d != null && d.getMacAddress().equalsIgnoreCase(expectedMac)) {
                            if (attemptOutcomeHandled.compareAndSet(false, true)) {
                                finishSuccess(d);
                            }
                        }
                    }

                    @Override
                    public void onBluetoothDisconnected(RxBleDevice d) {}

                    @Override
                    public void onBluetoothError(RxBleDevice d, String error) {
                        if (!isConnecting) {
                            return;
                        }
                        if (d != null && d.getMacAddress().equalsIgnoreCase(expectedMac)) {
                            if (attemptOutcomeHandled.compareAndSet(false, true)) {
                                onAttemptFailed(onFailureNext);
                            }
                        }
                    }

                    @Override
                    public void onBluetoothRssiChanged(RxBleDevice d, int rssi) {}
                };
        bluetoothService.addConnectionStateListener(attemptListener);
        bluetoothService.setReconnectSuppressed(true);
        Log.v(TAG, "Auto-connect attempt to " + expectedMac);
        bluetoothService.connectToDevice(device);
        attemptTimeoutRunnable =
                () -> {
                    if (!isConnecting) {
                        return;
                    }
                    if (bluetoothService.isConnected()
                            && bluetoothService.getConnectedDevice() != null
                            && bluetoothService.getConnectedDevice()
                                    .getMacAddress()
                                    .equalsIgnoreCase(expectedMac)) {
                        if (attemptOutcomeHandled.compareAndSet(false, true)) {
                            finishSuccess(bluetoothService.getConnectedDevice());
                        }
                        return;
                    }
                    if (attemptOutcomeHandled.compareAndSet(false, true)) {
                        onAttemptFailed(onFailureNext);
                    }
                };
        handler.postDelayed(attemptTimeoutRunnable, PER_DEVICE_TIMEOUT_MS);
    }

    private void onAttemptFailed(Runnable onFailureNext) {
        if (!isConnecting) {
            return;
        }
        unregisterAttemptListener();
        cancelAttemptTimeout();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        handler.postDelayed(
                () -> {
                    if (!isConnecting) {
                        return;
                    }
                    onFailureNext.run();
                },
                500);
    }

    private void tryScanFallbackOnly() {
        if (!isConnecting) {
            return;
        }
        Set<String> pairedMacs = new HashSet<>();
        if (connectionManager != null) {
            for (PairedBleDevice p : connectionManager.getPairedDevicesByRecency()) {
                pairedMacs.add(p.mac.toUpperCase(Locale.US));
            }
        }
        final List<ScanResult> candidates = new ArrayList<>();
        ScanSettings scanSettings =
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
        scanDisposable =
                rxBleClient.scanBleDevices(scanSettings)
                        .timeout(SCAN_DURATION_MS, TimeUnit.MILLISECONDS)
                        .subscribe(
                                scanResult -> {
                                    RxBleDevice device = scanResult.getBleDevice();
                                    String name = sanitizeDeviceName(device.getName());
                                    String deviceMac = device.getMacAddress();
                                    if (!OpenterfaceBleDeviceNames.matchesAdvertisedName(name)) {
                                        return;
                                    }
                                    if (!pairedMacs.contains(deviceMac.toUpperCase(Locale.US))) {
                                        return;
                                    }
                                    boolean exists = false;
                                    for (ScanResult r : candidates) {
                                        if (r.getBleDevice().getMacAddress().equalsIgnoreCase(deviceMac)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        candidates.add(scanResult);
                                    }
                                },
                                throwable -> handleScanComplete(candidates),
                                () -> handleScanComplete(candidates));
    }

    private void handleScanComplete(List<ScanResult> candidates) {
        if (!isConnecting) {
            return;
        }
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
        if (candidates.size() == 1) {
            RxBleDevice d = candidates.get(0).getBleDevice();
            startAttemptForMac(d.getMacAddress(), () -> handleRoundFailure("No reachable paired device"));
            return;
        }
        handleRoundFailure(
                candidates.isEmpty()
                        ? "No paired Openterface devices in range"
                        : "Multiple devices in range; open Bluetooth to pick one");
    }

    private void handleRoundFailure(String reason) {
        currentRound++;
        if (currentRound < MAX_ROUNDS) {
            Log.v(TAG, "Round failed: " + reason + ", retry " + currentRound + "/" + MAX_ROUNDS);
            if (listener != null) {
                listener.onAutoConnectRetrying(currentRound);
            }
            unregisterAttemptListener();
            cancelAttemptTimeout();
            if (bluetoothService != null) {
                bluetoothService.disconnect();
            }
            handler.postDelayed(this::beginRound, RETRY_DELAY_MS);
        } else {
            Log.e(TAG, "Auto-connect failed after " + MAX_ROUNDS + " rounds: " + reason);
            cleanupSession();
            notifyFailed("Failed after " + MAX_ROUNDS + " attempts: " + reason);
        }
    }

    private synchronized void finishSuccess(RxBleDevice device) {
        if (!isConnecting) {
            return;
        }
        unregisterAttemptListener();
        cancelAttemptTimeout();
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
        cleanupSession();
        Log.v(TAG, "Successfully connected to " + sanitizeDeviceName(device.getName()));
        notifySuccess(device);
    }

    private void cleanupSession() {
        isConnecting = false;
        if (bluetoothService != null) {
            bluetoothService.setReconnectSuppressed(false);
        }
    }

    public void stopAutoConnect() {
        isConnecting = false;
        unregisterAttemptListener();
        cancelAttemptTimeout();
        if (scanDisposable != null && !scanDisposable.isDisposed()) {
            scanDisposable.dispose();
        }
        handler.removeCallbacksAndMessages(null);
        if (bluetoothService != null) {
            bluetoothService.setReconnectSuppressed(false);
        }
        Log.v(TAG, "Auto-connect stopped");
    }

    private void unregisterAttemptListener() {
        if (bluetoothService != null && attemptListener != null) {
            bluetoothService.removeConnectionStateListener(attemptListener);
        }
        attemptListener = null;
    }

    private void cancelAttemptTimeout() {
        if (attemptTimeoutRunnable != null) {
            handler.removeCallbacks(attemptTimeoutRunnable);
        }
        attemptTimeoutRunnable = null;
    }

    private boolean hasRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            boolean hasBluetoothConnect =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothScan =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;
            return hasBluetoothConnect && hasBluetoothScan;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void notifySuccess(RxBleDevice device) {
        if (listener != null) {
            handler.post(() -> listener.onAutoConnectSuccess(device));
        }
    }

    private void notifyFailed(String reason) {
        if (listener != null) {
            handler.post(() -> listener.onAutoConnectFailed(reason));
        }
    }

    private String sanitizeDeviceName(String name) {
        if (name == null) {
            return "Unknown";
        }
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }
}
