package com.openterface.keymod;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

public class BluetoothDialogFragment extends DialogFragment {

    private static final String TAG = "BluetoothDialogFragment";
    private static final String LOG_PREFIX = "[Bluetooth] ";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1002;
    private static final int SCAN_DURATION_MS = 12000;

    private BluetoothAdapter bluetoothAdapter;
    private Switch bluetoothSwitch;
    private ListView devicesListView;
    private Button scanButton;
    private ListDeviceAdapter devicesAdapter;
    private final ArrayList<ListRow> rows = new ArrayList<>();
    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private final Object scanLock = new Object();
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    private ConnectionManager connectionManager;

    /** MAC upper case -> RSSI from current scan session */
    private final Map<String, Integer> rssiByMac = new HashMap<>();

    private final ArrayList<RxBleDevice> scannedDevices = new ArrayList<>();
    private Runnable scanEndRunnable;

    public interface BluetoothConnectionListener {
        void onBluetoothConnectionChanged(boolean isConnected);
    }

    private BluetoothConnectionListener connectionListener;

    private final BluetoothService.ConnectionStateListener bleStateListener =
            new BluetoothService.ConnectionStateListener() {
                @Override
                public void onBluetoothConnecting(RxBleDevice device) {
                    postRebuild();
                }

                @Override
                public void onBluetoothConnected(RxBleDevice device) {
                    if (device != null) {
                        String mac = device.getMacAddress().toUpperCase(Locale.US);
                        scannedDevices.removeIf(d -> d.getMacAddress().equalsIgnoreCase(mac));
                    }
                    postRebuild();
                }

                @Override
                public void onBluetoothDisconnected(RxBleDevice device) {
                    postRebuild();
                }

                @Override
                public void onBluetoothError(RxBleDevice device, String error) {
                    postRebuild();
                }

                @Override
                public void onBluetoothRssiChanged(RxBleDevice device, int rssi) {
                    if (device != null) {
                        rssiByMac.put(device.getMacAddress().toUpperCase(Locale.US), rssi);
                    }
                    postRebuild();
                }
            };

    private void postRebuild() {
        if (isAdded()) {
            mainHandler.post(this::rebuildDeviceList);
        }
    }

    private final ServiceConnection serviceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
                    bluetoothService = binder.getService();
                    isServiceBound = true;
                    bluetoothService.setRxBleClient(rxBleClient);
                    bluetoothService.addConnectionStateListener(bleStateListener);
                    boolean isConnected = bluetoothService.isConnected();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).isBluetoothConnected = isConnected;
                    }
                    if (connectionListener != null) {
                        connectionListener.onBluetoothConnectionChanged(isConnected);
                    }
                    if (bluetoothAdapter != null
                            && bluetoothAdapter.isEnabled()
                            && isServiceBound
                            && bluetoothService != null) {
                        RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
                        if (connectedDevice != null) {
                            rebuildDeviceList();
                        } else {
                            connectMostRecentPairedIfIdle();
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (bluetoothService != null) {
                        bluetoothService.removeConnectionStateListener(bleStateListener);
                    }
                    isServiceBound = false;
                    bluetoothService = null;
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).isBluetoothConnected = false;
                    }
                    if (connectionListener != null) {
                        connectionListener.onBluetoothConnectionChanged(false);
                    }
                }
            };

    private static final int TYPE_HEADER_PAIRED = 0;
    private static final int TYPE_ROW_PAIRED = 1;
    private static final int TYPE_HEADER_SCANNED = 2;
    private static final int TYPE_ROW_SCANNED = 3;

    private static class ListRow {
        final int type;
        final String headerTitle;
        final PairedBleDevice paired;
        final RxBleDevice scannedDevice;
        final int rssi;

        static ListRow headerPaired(@NonNull Context ctx) {
            return new ListRow(TYPE_HEADER_PAIRED, ctx.getString(R.string.bt_header_paired_devices), null, null, 0);
        }

        static ListRow headerScanned(@NonNull Context ctx) {
            return new ListRow(TYPE_HEADER_SCANNED, ctx.getString(R.string.bt_header_new_devices_scan), null, null, 0);
        }

        static ListRow paired(PairedBleDevice p, int rssi) {
            return new ListRow(TYPE_ROW_PAIRED, null, p, null, rssi);
        }

        static ListRow scanned(RxBleDevice d, int rssi) {
            return new ListRow(TYPE_ROW_SCANNED, null, null, d, rssi);
        }

        private ListRow(
                int type,
                String headerTitle,
                PairedBleDevice paired,
                RxBleDevice scannedDevice,
                int rssi) {
            this.type = type;
            this.headerTitle = headerTitle;
            this.paired = paired;
            this.scannedDevice = scannedDevice;
            this.rssi = rssi;
        }
    }

    private class ListDeviceAdapter extends ArrayAdapter<ListRow> {
        ListDeviceAdapter(Context context, ArrayList<ListRow> list) {
            super(context, 0, list);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ListRow row = getItem(position);
            if (row == null) {
                return convertView;
            }
            int type = row.type;
            if (type == TYPE_HEADER_PAIRED || type == TYPE_HEADER_SCANNED) {
                if (convertView == null
                        || convertView.getTag() == null
                        || !convertView.getTag().equals("HEADER")) {
                    convertView =
                            LayoutInflater.from(getContext())
                                    .inflate(android.R.layout.simple_list_item_1, parent, false);
                    convertView.setTag("HEADER");
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                textView.setText(row.headerTitle);
                textView.setTextSize(14);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                convertView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.bluetooth_surface));
                convertView.setEnabled(false);
                convertView.setOnClickListener(null);
            } else {
                if (convertView == null
                        || convertView.getTag() == null
                        || !convertView.getTag().equals("DEVICE")) {
                    convertView =
                            LayoutInflater.from(getContext())
                                    .inflate(R.layout.list_item_bluetooth_device, parent, false);
                    convertView.setTag("DEVICE");
                }
                TextView nameView = convertView.findViewById(R.id.device_name);
                TextView addressView = convertView.findViewById(R.id.device_address);
                TextView detailView = convertView.findViewById(R.id.last_connected);

                if (type == TYPE_ROW_PAIRED && row.paired != null) {
                    String mac = row.paired.mac;
                    nameView.setText(row.paired.name);
                    addressView.setText(mac);
                    String status = pairedStatusLine(mac, row.rssi);
                    detailView.setText(status);
                    detailView.setVisibility(View.VISIBLE);
                } else if (type == TYPE_ROW_SCANNED && row.scannedDevice != null) {
                    String deviceName = sanitizeDeviceName(row.scannedDevice.getName());
                    nameView.setText(deviceName);
                    addressView.setText(row.scannedDevice.getMacAddress());
                    if (row.rssi != 0) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            String distance = BluetoothDialogFragment.this.rssiToEstimatedDistance(row.rssi);
                            String signalStrength =
                                    row.rssi >= -50
                                            ? ctx.getString(R.string.bt_signal_excellent)
                                            : row.rssi >= -70
                                                    ? ctx.getString(R.string.bt_signal_good)
                                                    : ctx.getString(R.string.bt_signal_weak);
                            detailView.setText(
                                    ctx.getString(
                                            R.string.bt_scanned_device_detail,
                                            signalStrength,
                                            row.rssi,
                                            distance));
                        }
                        detailView.setVisibility(View.VISIBLE);
                    } else {
                        detailView.setVisibility(View.GONE);
                    }
                }
            }
            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).type;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEnabled(int position) {
            int t = getItem(position).type;
            return t == TYPE_ROW_PAIRED || t == TYPE_ROW_SCANNED;
        }
    }

    private String pairedStatusLine(String macUpper, int scanRssi) {
        if (isServiceBound
                && bluetoothService != null
                && bluetoothService.isConnected()
                && bluetoothService.getConnectedDevice() != null
                && bluetoothService.getConnectedDevice().getMacAddress().equalsIgnoreCase(macUpper)) {
            if (scanRssi != 0) {
                return getString(R.string.bt_paired_connected_rssi, scanRssi);
            }
            return getString(R.string.connected);
        }
        String last = formatRelativeTime(getCm().getPairedDevicesByRecency(), macUpper);
        if (scanRssi != 0) {
            if (last != null) {
                return getString(R.string.bt_paired_last_and_rssi, last, scanRssi);
            }
            return getString(R.string.bt_paired_in_range_rssi, scanRssi);
        }
        return last != null ? last : getString(R.string.bt_paired_saved_not_in_range);
    }

    private String formatRelativeTime(List<PairedBleDevice> pairedList, String macUpper) {
        for (PairedBleDevice p : pairedList) {
            if (p.mac.equalsIgnoreCase(macUpper)) {
                return formatAge(p.lastConnectedAtMs);
            }
        }
        return null;
    }

    private String formatAge(long lastMs) {
        long diff = System.currentTimeMillis() - lastMs;
        int minutes = (int) (diff / 60000);
        int hours = (int) (diff / 3600000);
        int days = (int) (diff / 86400000);
        if (minutes < 1) {
            return getString(R.string.bt_last_used_just_now);
        }
        if (minutes < 60) {
            return getString(R.string.bt_last_used_minutes_ago, minutes);
        }
        if (hours < 24) {
            return getString(R.string.bt_last_used_hours_ago, hours);
        }
        if (days < 7) {
            return getString(R.string.bt_last_used_days_ago, days);
        }
        String dateStr =
                new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new java.util.Date(lastMs));
        return getString(R.string.bt_last_used_date, dateStr);
    }

    private String sanitizeDeviceName(String name) {
        if (name == null) {
            return getString(R.string.bt_device_name_unknown);
        }
        return name.replaceAll("[^\\p{Print}]", "").trim();
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    public void setConnectionManager(ConnectionManager manager) {
        this.connectionManager = manager;
    }

    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    private ConnectionManager getCm() {
        if (connectionManager != null) {
            return connectionManager;
        }
        return new ConnectionManager(requireContext());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_bluetooth, container, false);
        initializeUIComponents(view);
        initializeBluetooth();
        bindService();
        if (!isServiceBound) {
            bindService();
        }
        rebuildDeviceList();
        return view;
    }

    private void bindService() {
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(true);
        }
        if (getDialog() != null && getDialog().getWindow() != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            boolean isLandscape =
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            int dialogWidth = isLandscape ? (int) (screenWidth * 0.9) : (int) (screenWidth * 0.666);
            int dialogHeight = isLandscape ? (int) (screenHeight * 0.88) : (int) (screenHeight * 0.666);

            getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            Log.d(TAG, LOG_PREFIX + "Dialog size set to width: " + dialogWidth + ", height: " + dialogHeight);
        }
    }

    private void initializeUIComponents(View view) {
        bluetoothSwitch = view.findViewById(R.id.bluetooth_switch);
        devicesListView = view.findViewById(R.id.devices_list);
        scanButton = view.findViewById(R.id.scan_button);

        devicesAdapter = new ListDeviceAdapter(requireContext(), rows);
        devicesListView.setAdapter(devicesAdapter);

        bluetoothSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (isChecked) {
                        enableBluetooth();
                    } else {
                        disableBluetooth();
                    }
                });

        devicesListView.setOnItemClickListener(
                (parent, v, position, id) -> {
                    ListRow row = rows.get(position);
                    if (row.type == TYPE_ROW_PAIRED && row.paired != null) {
                        if (isCurrentlyConnectedTo(row.paired.mac)) {
                            confirmDisconnect(row.paired.name);
                        } else {
                            connectToMac(row.paired.mac, row.paired.name);
                        }
                    } else if (row.type == TYPE_ROW_SCANNED && row.scannedDevice != null) {
                        String n = sanitizeDeviceName(row.scannedDevice.getName());
                        if (OpenterfaceBleDeviceNames.matchesAdvertisedName(n)) {
                            connectToMac(row.scannedDevice.getMacAddress(), n);
                        } else {
                            showToast(getString(R.string.bt_toast_select_supported_device));
                        }
                    }
                });

        devicesListView.setOnItemLongClickListener(
                (parent, v, position, id) -> {
                    ListRow row = rows.get(position);
                    if (row.type != TYPE_ROW_PAIRED || row.paired == null) {
                        showToast(getString(R.string.bt_toast_only_saved_can_forget));
                        return false;
                    }
                    String mac = row.paired.mac;
                    String deviceName = row.paired.name;
                    if (isServiceBound
                            && bluetoothService.isConnected()
                            && bluetoothService.getConnectedDevice() != null
                            && bluetoothService.getConnectedDevice().getMacAddress().equalsIgnoreCase(mac)) {
                        bluetoothService.disconnect();
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.bt_forget_device_title)
                            .setMessage(
                                    getString(R.string.bt_forget_device_message, deviceName, mac))
                            .setPositiveButton(
                                    R.string.bt_forget_confirm,
                                    (dialog, which) -> {
                                        getCm().forgetPairedDevice(mac);
                                        scannedDevices.removeIf(d -> d.getMacAddress().equalsIgnoreCase(mac));
                                        rebuildDeviceList();
                                        showToast(
                                                getString(R.string.bt_device_forgotten_toast, deviceName));
                                    })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return true;
                });

        scanButton.setOnClickListener(
                v -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        if (!isScanning) {
                            startBleScan();
                        } else {
                            showToast(getString(R.string.bt_toast_scan_already_in_progress));
                        }
                    } else {
                        showToast(getString(R.string.bt_toast_enable_bluetooth_first));
                    }
                });
    }

    private boolean isCurrentlyConnectedTo(String mac) {
        return isServiceBound
                && bluetoothService != null
                && bluetoothService.isConnected()
                && bluetoothService.getConnectedDevice() != null
                && bluetoothService.getConnectedDevice().getMacAddress().equalsIgnoreCase(mac);
    }

    private void confirmDisconnect(String displayName) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.bt_disconnect_confirm_title)
                .setMessage(getString(R.string.bt_disconnect_confirm_message, displayName))
                .setPositiveButton(
                        R.string.bt_disconnect_confirm,
                        (d, w) -> {
                            if (isServiceBound && bluetoothService != null) {
                                bluetoothService.disconnect();
                            }
                            showToast(getString(R.string.bt_disconnected_toast, displayName));
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void connectToMac(String mac, String displayNameForToast) {
        if (!isServiceBound || bluetoothService == null) {
            showToast(getString(R.string.bt_toast_service_unavailable));
            return;
        }
        if (rxBleClient == null) {
            return;
        }
        RxBleDevice device = rxBleClient.getBleDevice(mac);
        if (bluetoothService.isConnected()
                && bluetoothService.getConnectedDevice() != null
                && bluetoothService.getConnectedDevice().getMacAddress().equalsIgnoreCase(mac)) {
            showToast(getString(R.string.bt_toast_device_already_connected));
            return;
        }
        showToast(getString(R.string.bt_toast_connecting_to, displayNameForToast));
        bluetoothService.connectToDevice(device);
    }

    private void connectMostRecentPairedIfIdle() {
        if (bluetoothAdapter == null
                || !bluetoothAdapter.isEnabled()
                || !isServiceBound
                || bluetoothService == null
                || bluetoothService.isConnected()
                || isScanning) {
            return;
        }
        List<PairedBleDevice> pl = getCm().getPairedDevicesByRecency();
        if (!pl.isEmpty()) {
            bluetoothService.connectToDevice(rxBleClient.getBleDevice(pl.get(0).mac));
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, LOG_PREFIX + "Bluetooth not supported on this device");
            showToast(getString(R.string.bt_toast_bt_not_supported));
            bluetoothSwitch.setEnabled(false);
            return;
        }

        boolean hasRequiredPermissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            boolean hasBluetoothConnect =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothScan =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;
            hasRequiredPermissions = hasBluetoothConnect && hasBluetoothScan;
        } else {
            hasRequiredPermissions =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
        }

        if (hasRequiredPermissions) {
            bluetoothSwitch.setEnabled(true);
            bluetoothSwitch.setChecked(bluetoothAdapter.isEnabled());
        } else {
            checkPermissions();
        }
    }

    private void checkPermissions() {
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions =
                    new String[] {
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                    };
        } else {
            permissions = new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
        }

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissions(permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            initializeBluetooth();
        }
    }

    private void enableBluetooth() {
        if (bluetoothAdapter == null) {
            return;
        }

        boolean hasPermission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            hasPermission =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasPermission) {
            checkPermissions();
            bluetoothSwitch.setChecked(false);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else if (isServiceBound && bluetoothService.isConnected()) {
            showToast(getString(R.string.bt_toast_already_connected_one_device));
            rebuildDeviceList();
        } else if (!isScanning && isServiceBound) {
            connectMostRecentPairedIfIdle();
            if (getCm().getPairedDevicesByRecency().isEmpty()) {
                startBleScan();
            }
        } else if (!isScanning) {
            startBleScan();
        }
    }

    private void disableBluetooth() {
        if (bluetoothAdapter == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            showToast(getString(R.string.bt_toast_bt_permissions_required));
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            stopScan();
            rebuildDeviceList();
            showToast(getString(R.string.bt_toast_bt_disabled));
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionChanged(false);
            }
        }
    }

    private void startBleScan() {
        if (rxBleClient == null) {
            Log.e(TAG, LOG_PREFIX + "RxBleClient is not initialized");
            showToast(getString(R.string.bt_toast_scanning_unavailable));
            return;
        }

        boolean hasPermission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            hasPermission =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission =
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasPermission) {
            Log.w(TAG, LOG_PREFIX + "Required permission not granted for BLE scanning");
            checkPermissions();
            return;
        }

        synchronized (scanLock) {
            if (isScanning) {
                Log.d(TAG, LOG_PREFIX + "Scan already in progress, ignoring startBleScan");
                return;
            }
            isScanning = true;
            mainHandler.post(() -> scanButton.setEnabled(false));
        }

        Log.d(TAG, LOG_PREFIX + "Starting BLE scan for Openterface / KeyCmd devices...");
        showToast(getString(R.string.bt_toast_scanning_started));

        rssiByMac.clear();
        scannedDevices.clear();
        rebuildDeviceList();

        if (scanEndRunnable != null) {
            mainHandler.removeCallbacks(scanEndRunnable);
        }
        scanEndRunnable =
                () -> {
                    stopScan();
                    rebuildDeviceList();
                };
        mainHandler.postDelayed(scanEndRunnable, SCAN_DURATION_MS);

        ScanSettings scanSettings =
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        synchronized (scanLock) {
            scanSubscription =
                    rxBleClient
                            .scanBleDevices(scanSettings)
                            .timeout(SCAN_DURATION_MS, TimeUnit.MILLISECONDS)
                            .subscribe(
                                    scanResult -> {
                                        RxBleDevice device = scanResult.getBleDevice();
                                        String deviceName = sanitizeDeviceName(device.getName());
                                        String deviceAddress = device.getMacAddress();
                                        if (!OpenterfaceBleDeviceNames.matchesAdvertisedName(deviceName)) {
                                            return;
                                        }
                                        int rssi = scanResult.getRssi();
                                        synchronized (scanLock) {
                                            rssiByMac.put(deviceAddress.toUpperCase(Locale.US), rssi);
                                            if (getCm().isPairedBleMac(deviceAddress)) {
                                                mainHandler.post(this::rebuildDeviceList);
                                                return;
                                            }
                                            boolean exists = false;
                                            for (RxBleDevice d : scannedDevices) {
                                                if (d.getMacAddress().equalsIgnoreCase(deviceAddress)) {
                                                    exists = true;
                                                    break;
                                                }
                                            }
                                            if (!exists) {
                                                scannedDevices.add(device);
                                            }
                                            mainHandler.post(this::rebuildDeviceList);
                                        }
                                    },
                                    throwable -> {
                                        Log.e(TAG, LOG_PREFIX + "Scan error: " + throwable.getMessage(), throwable);
                                        showToast(getString(R.string.bt_toast_scan_finished));
                                        synchronized (scanLock) {
                                            isScanning = false;
                                            mainHandler.post(() -> scanButton.setEnabled(true));
                                        }
                                        if (scanEndRunnable != null) {
                                            mainHandler.removeCallbacks(scanEndRunnable);
                                        }
                                        rebuildDeviceList();
                                    },
                                    () -> {
                                        synchronized (scanLock) {
                                            isScanning = false;
                                            mainHandler.post(() -> scanButton.setEnabled(true));
                                        }
                                        if (scanEndRunnable != null) {
                                            mainHandler.removeCallbacks(scanEndRunnable);
                                        }
                                        rebuildDeviceList();
                                    });
        }
    }

    private void stopScan() {
        synchronized (scanLock) {
            if (scanSubscription != null && !scanSubscription.isDisposed()) {
                scanSubscription.dispose();
            }
            if (scanEndRunnable != null) {
                mainHandler.removeCallbacks(scanEndRunnable);
                scanEndRunnable = null;
            }
            if (isScanning) {
                isScanning = false;
                mainHandler.post(() -> scanButton.setEnabled(true));
            }
        }
    }

    private void rebuildDeviceList() {
        rows.clear();
        rows.add(ListRow.headerPaired(requireContext()));
        List<PairedBleDevice> paired = getCm().getPairedDevicesByRecency();
        for (PairedBleDevice p : paired) {
            int rssi = rssiByMac.getOrDefault(p.mac.toUpperCase(Locale.US), 0);
            rows.add(ListRow.paired(p, rssi));
        }
        rows.add(ListRow.headerScanned(requireContext()));
        for (RxBleDevice d : scannedDevices) {
            int rssi = rssiByMac.getOrDefault(d.getMacAddress().toUpperCase(Locale.US), 0);
            rows.add(ListRow.scanned(d, rssi));
        }
        if (devicesAdapter != null) {
            mainHandler.post(() -> devicesAdapter.notifyDataSetChanged());
        }
    }

    private String rssiToEstimatedDistance(int rssi) {
        if (rssi == 0) {
            return "?";
        }
        double txPower = -59.0;
        double n = 2.0;
        double distance = Math.pow(10.0, (txPower - rssi) / (10.0 * n));
        if (distance < 1.0) {
            return getString(R.string.bt_distance_cm, (int) (distance * 100));
        } else if (distance < 10.0) {
            return getString(R.string.bt_distance_m_one, distance);
        } else {
            return getString(R.string.bt_distance_m_many, distance);
        }
    }

    private void showToast(String message) {
        if (isAdded() && getContext() != null) {
            mainHandler.post(() -> UiToastLimiter.show(getContext(), message));
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[i])) {
                        showPermissionSettingsDialog();
                    }
                }
            }
            if (allGranted && isServiceBound) {
                connectMostRecentPairedIfIdle();
            } else {
                showToast(getString(R.string.bt_toast_permissions_denied));
                bluetoothSwitch.setEnabled(false);
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.bt_permission_dialog_title)
                .setMessage(R.string.bt_permission_dialog_message)
                .setPositiveButton(
                        R.string.bt_open_system_settings,
                        (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                            startActivity(intent);
                        })
                .setNegativeButton(R.string.settings_history_cancel, (dialog, which) -> dismiss())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == requireActivity().RESULT_OK) {
                showToast(getString(R.string.bt_toast_bt_enabled));
                if (isServiceBound) {
                    connectMostRecentPairedIfIdle();
                }
            } else {
                bluetoothSwitch.setChecked(false);
                showToast(getString(R.string.bt_toast_bt_not_enabled));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
        if (bluetoothService != null) {
            bluetoothService.removeConnectionStateListener(bleStateListener);
        }
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        Log.d(TAG, LOG_PREFIX + "Dialog destroyed, connection maintained by BluetoothService");
    }
}
