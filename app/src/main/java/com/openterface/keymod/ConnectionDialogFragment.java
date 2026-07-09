package com.openterface.keymod;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.polidea.rxandroidble2.RxBleClient;

/**
 * Dialog Fragment for managing device connections (USB and Bluetooth)
 */
public class ConnectionDialogFragment extends DialogFragment {
    
    private static final String TAG = "ConnectionDialog";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;

    private ConnectionManager connectionManager;
    private RxBleClient rxBleClient;
    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;

    // UI Components
    private TextView connectionStatus;
    private CardView usbCard;
    private CardView bluetoothCard;
    private TextView usbStatus;
    private TextView bluetoothStatus;
    private ImageView usbStatusIcon;
    private ImageView bluetoothStatusIcon;
    private ImageView usbSignal;
    private ImageView bluetoothSignal;
    private CheckBox autoConnectCheckbox;
    private TextView lastConnectedInfo;
    private final ConnectionManager.ConnectionStateListener connectionStateListener =
            new ConnectionManager.ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(
                        ConnectionManager.ConnectionType type,
                        ConnectionManager.ConnectionState state) {
                    if (getActivity() != null) {
                        getActivity()
                                .runOnUiThread(
                                        () -> {
                                            updateUI();
                                            if (listener != null) {
                                                listener.onConnectionChanged(type, state);
                                            }
                                        });
                    }
                }

                @Override
                public void onConnectionError(String error) {
                    if (getActivity() != null) {
                        getActivity()
                                .runOnUiThread(
                                        () ->
                                                UiToastLimiter.show(
                                                        requireContext(),
                                                        "connection_error",
                                                        error,
                                                        android.widget.Toast.LENGTH_SHORT,
                                                        1800));
                    }
                }

                @Override
                public void onBluetoothRssiChanged(int rssi) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(ConnectionDialogFragment.this::updateUI);
                    }
                }
            };

    public interface ConnectionDialogListener {
        void onConnectionChanged(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state);
    }

    private ConnectionDialogListener listener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            Log.v(TAG, "Bound to BluetoothService");
            updateBluetoothStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.v(TAG, "Unbound from BluetoothService");
        }
    };

    public static ConnectionDialogFragment newInstance() {
        return new ConnectionDialogFragment();
    }

    public void setConnectionManager(ConnectionManager manager) {
        this.connectionManager = manager;
    }

    public void setRxBleClient(RxBleClient client) {
        this.rxBleClient = client;
    }

    public void setConnectionDialogListener(ConnectionDialogListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use app theme so light/dark mode is applied automatically.
        setStyle(DialogFragment.STYLE_NORMAL, 0);
        setCancelable(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set dialog width and transparent window background so rounded corners render cleanly.
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().setCanceledOnTouchOutside(true);
            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int widthFactor = isLandscape ? 92 : 90;
            int width = (getResources().getDisplayMetrics().widthPixels * widthFactor) / 100;
            int height = isLandscape ? (getResources().getDisplayMetrics().heightPixels * 88) / 100 : ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(width, height);
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_connection, container, false);
        initViews(view);
        setupListeners();
        updateUI();
        
        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        return view;
    }

    private void initViews(View view) {
        connectionStatus = view.findViewById(R.id.connection_status);
        usbCard = view.findViewById(R.id.usb_card);
        bluetoothCard = view.findViewById(R.id.bluetooth_card);
        usbStatus = view.findViewById(R.id.usb_status);
        bluetoothStatus = view.findViewById(R.id.bluetooth_status);
        usbStatusIcon = view.findViewById(R.id.usb_status_icon);
        bluetoothStatusIcon = view.findViewById(R.id.bluetooth_status_icon);
        usbSignal = view.findViewById(R.id.usb_signal);
        bluetoothSignal = view.findViewById(R.id.bluetooth_signal);
        autoConnectCheckbox = view.findViewById(R.id.auto_connect_checkbox);
        lastConnectedInfo = view.findViewById(R.id.last_connected_info);

        // Set initial auto-connect state
        if (connectionManager != null) {
            autoConnectCheckbox.setChecked(connectionManager.isAutoConnectEnabled());
        }
    }

    private void setupListeners() {
        usbCard.setOnClickListener(v -> {
            Log.v(TAG, "USB card clicked");
            handleUsbConnection();
        });

        bluetoothCard.setOnClickListener(v -> {
            Log.v(TAG, "Bluetooth card clicked");
            handleBluetoothConnection();
        });

        autoConnectCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (connectionManager != null) {
                connectionManager.setAutoConnectEnabled(isChecked);
            }
        });

        // Observe connection state without replacing activity listeners.
        if (connectionManager != null) {
            connectionManager.addConnectionStateListener(connectionStateListener);
        }
    }

    private void handleUsbConnection() {
        if (connectionManager == null) return;

        ConnectionManager.ConnectionType currentType = connectionManager.getCurrentConnectionType();
        
        if (currentType == ConnectionManager.ConnectionType.USB && 
            connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED) {
            // Disconnect USB
            connectionManager.disconnect();
            UiToastLimiter.show(requireContext(), "usb_disconnected",
                    getString(R.string.connection_toast_usb_disconnected), android.widget.Toast.LENGTH_SHORT, 1800);
        } else {
            // Disconnect Bluetooth if connected
            if (currentType == ConnectionManager.ConnectionType.BLUETOOTH && isServiceBound && bluetoothService != null) {
                bluetoothService.disconnect();
            }
            
            // Connect USB
            boolean success = connectionManager.connectUsb();
            if (success) {
                UiToastLimiter.show(requireContext(), "usb_connected",
                        getString(R.string.connection_toast_usb_connected), android.widget.Toast.LENGTH_SHORT, 1800);
            }
        }
        
        updateUI();
    }

    private void handleBluetoothConnection() {
        if (connectionManager == null) return;

        Log.v(TAG, "handleBluetoothConnection called");
        ConnectionManager.ConnectionType currentType = connectionManager.getCurrentConnectionType();

        // Always open Bluetooth Settings from this card (disconnect only from that screen).
        if (currentType == ConnectionManager.ConnectionType.USB) {
            Log.v(TAG, "Disconnecting USB first before Bluetooth settings");
            connectionManager.disconnect();
        }

        if (checkBluetoothPermissions()) {
            Log.v(TAG, "Permissions granted, showing Bluetooth dialog");
            showBluetoothDeviceDialog();
        } else {
            Log.v(TAG, "Requesting Bluetooth permissions");
            requestBluetoothPermissions();
        }

        updateUI();
    }

    private void showBluetoothDeviceDialog() {
        Log.v(TAG, "showBluetoothDeviceDialog called");
        if (getParentFragmentManager().findFragmentByTag("BluetoothDialog") != null) {
            Log.v(TAG, "Bluetooth dialog already shown; skipping duplicate show");
            return;
        }
        // Show the existing BluetoothDialogFragment
        BluetoothDialogFragment dialog = new BluetoothDialogFragment();
        dialog.setRxBleClient(rxBleClient);
        if (connectionManager != null) {
            dialog.setConnectionManager(connectionManager);
        }
        dialog.setConnectionListener(isConnected -> {
            Log.v(TAG, "Bluetooth connection state changed: " + isConnected);
            if (isConnected) {
                // Wait a bit for the service to update
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isServiceBound && bluetoothService != null && connectionManager != null) {
                        com.polidea.rxandroidble2.RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
                        if (connectedDevice != null) {
                            Log.v(TAG, "Updating ConnectionManager with BLE device: " + connectedDevice.getMacAddress());
                            connectionManager.connectBluetooth(connectedDevice);
                        } else {
                            Log.w(TAG, "Connected device is null");
                        }
                    } else {
                        Log.w(TAG, "Service not bound or ConnectionManager is null - isServiceBound: " + isServiceBound + ", bluetoothService: " + (bluetoothService != null) + ", connectionManager: " + (connectionManager != null));
                    }
                    updateUI();
                }, 500);
            } else {
                if (connectionManager != null && 
                    connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH) {
                    connectionManager.disconnect();
                }
                updateUI();
            }
        });
        
        try {
            Log.v(TAG, "Showing BluetoothDialogFragment");
            dialog.show(getParentFragmentManager(), "BluetoothDialog");
        } catch (Exception e) {
            Log.e(TAG, "Error showing BluetoothDialogFragment: " + e.getMessage(), e);
            UiToastLimiter.show(requireContext(), "bt_dialog_open_error",
                    getString(R.string.connection_toast_bt_dialog_error, e.getMessage()), android.widget.Toast.LENGTH_LONG, 2500);
        }
    }

    private boolean checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 and above
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 and below - need location permission for BLE scanning
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        Log.v(TAG, "Requesting permissions from Fragment");
        UiToastLimiter.show(requireContext(), "bt_permission_request",
                getString(R.string.connection_toast_requesting_bt_permissions), android.widget.Toast.LENGTH_SHORT, 2000);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12 and above
            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // Android 11 and below
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void updateUI() {
        if (connectionManager == null) return;

        ConnectionManager.ConnectionType type = connectionManager.getCurrentConnectionType();
        ConnectionManager.ConnectionState state = connectionManager.getCurrentConnectionState();

        // Update main status text
        String statusText = "";
        switch (state) {
            case CONNECTED:
                statusText = getString(
                        R.string.connection_status_connected_line,
                        getString(R.string.status_connected),
                        type == ConnectionManager.ConnectionType.USB
                                ? getString(R.string.connection_medium_usb)
                                : getString(R.string.connection_medium_bluetooth));
                break;
            case CONNECTING:
                statusText = getString(R.string.status_connecting);
                break;
            case DISCONNECTED:
                statusText = getString(R.string.status_disconnected);
                break;
        }
        connectionStatus.setText(statusText);

        // Update USB status
        updateUsbStatus();

        // Update Bluetooth status
        updateBluetoothStatus();

        // Update last connected info
        updateLastConnectedInfo();
    }

    private void updateUsbStatus() {
        if (connectionManager == null) return;

        boolean isUsbConnected = connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.USB &&
                                connectionManager.getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED;

        int connected = ContextCompat.getColor(requireContext(), R.color.connected);
        int idle = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        if (isUsbConnected) {
            usbStatus.setText(R.string.connected);
            usbStatusIcon.setImageResource(R.drawable.ic_usb_24);
            usbStatusIcon.setColorFilter(connected, PorterDuff.Mode.SRC_IN);
            if (usbSignal != null) {
                usbSignal.setVisibility(View.VISIBLE);
                usbSignal.setColorFilter(connected, PorterDuff.Mode.SRC_IN);
            }
        } else {
            usbStatus.setText(R.string.not_connected);
            usbStatusIcon.setImageResource(R.drawable.ic_usb_off_24);
            usbStatusIcon.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            if (usbSignal != null) usbSignal.setVisibility(View.GONE);
        }
    }

    private void updateBluetoothStatus() {
        if (connectionManager == null) return;

        BluetoothManager btMgr = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btMgr != null ? btMgr.getAdapter() : null;
        boolean btOff = adapter == null || !adapter.isEnabled();

        boolean isBluetoothConnected =
                connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH
                        && connectionManager.getCurrentConnectionState()
                                == ConnectionManager.ConnectionState.CONNECTED;
        boolean isBluetoothConnecting =
                connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH
                        && connectionManager.getCurrentConnectionState()
                                == ConnectionManager.ConnectionState.CONNECTING;

        int connected = ThemeManager.getColorPrimary(requireContext());
        int idle = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        if (btOff) {
            bluetoothStatusIcon.clearAnimation();
            bluetoothStatus.setText(R.string.not_connected);
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth_disabled_24);
            bluetoothStatusIcon.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            if (bluetoothSignal != null) bluetoothSignal.setVisibility(View.GONE);
        } else if (isBluetoothConnected) {
            bluetoothStatusIcon.clearAnimation();
            String deviceName = connectionManager.getLastBleDeviceName();
            if (deviceName != null && !deviceName.isEmpty()) {
                bluetoothStatus.setText(
                        getString(
                                R.string.bluetooth_status_connected_with_hint,
                                deviceName,
                                getString(R.string.bluetooth_card_tap_hint)));
            } else {
                bluetoothStatus.setText(getString(R.string.connected));
            }
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth_connected_24);
            bluetoothStatusIcon.setColorFilter(connected, PorterDuff.Mode.SRC_IN);
            if (bluetoothSignal != null) {
                bluetoothSignal.setImageResource(connectionManager.getBleSignalDrawableRes());
                bluetoothSignal.setVisibility(View.VISIBLE);
                bluetoothSignal.setColorFilter(connected, PorterDuff.Mode.SRC_IN);
            }
        } else if (isBluetoothConnecting) {
            bluetoothStatus.setText(R.string.status_connecting);
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth_connecting_24);
            bluetoothStatusIcon.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            if (bluetoothStatusIcon.getAnimation() == null) {
                android.view.animation.Animation spin =
                        android.view.animation.AnimationUtils.loadAnimation(
                                requireContext(), R.anim.connection_spinning);
                bluetoothStatusIcon.startAnimation(spin);
            }
            if (bluetoothSignal != null) bluetoothSignal.setVisibility(View.GONE);
        } else {
            bluetoothStatusIcon.clearAnimation();
            bluetoothStatus.setText(R.string.not_connected);
            bluetoothStatusIcon.setImageResource(R.drawable.ic_bluetooth_24);
            bluetoothStatusIcon.setColorFilter(idle, PorterDuff.Mode.SRC_IN);
            if (bluetoothSignal != null) bluetoothSignal.setVisibility(View.GONE);
        }
    }

    private void updateLastConnectedInfo() {
        if (connectionManager == null) return;

        String lastType = connectionManager.getLastConnectionType();
        
        if ("NONE".equals(lastType) || lastType.isEmpty()) {
            lastConnectedInfo.setText(R.string.last_connected_none);
            lastConnectedInfo.setVisibility(View.VISIBLE);
        } else if ("USB".equals(lastType)) {
            lastConnectedInfo.setText(R.string.last_connected_usb);
            lastConnectedInfo.setVisibility(View.VISIBLE);
        } else if ("BLUETOOTH".equals(lastType)) {
            String deviceName = connectionManager.getLastBleDeviceName();
            if (deviceName != null) {
                lastConnectedInfo.setText(getString(R.string.last_connected_bluetooth, deviceName));
                lastConnectedInfo.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (connectionManager != null) {
            connectionManager.removeConnectionStateListener(connectionStateListener);
        }
        super.onDestroyView();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.v(TAG, "onRequestPermissionsResult called with requestCode: " + requestCode);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            Log.v(TAG, "Permission result - length: " + grantResults.length + ", result: " + (grantResults.length > 0 ? grantResults[0] : "none"));
            
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }
            
            if (allGranted) {
                Log.v(TAG, "Bluetooth permissions GRANTED, showing device dialog");
                UiToastLimiter.show(requireContext(), "bt_permission_granted",
                        getString(R.string.connection_toast_bt_permission_granted), android.widget.Toast.LENGTH_SHORT, 2000);
                showBluetoothDeviceDialog();
            } else {
                Log.v(TAG, "Bluetooth permissions DENIED");
                UiToastLimiter.show(requireContext(),
                    "bt_permission_denied",
                    getString(R.string.connection_toast_bt_permission_denied),
                    android.widget.Toast.LENGTH_LONG,
                    2500);
            }
        }
    }
}
