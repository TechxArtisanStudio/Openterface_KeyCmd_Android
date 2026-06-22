package com.openterface.terminal;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

/**
 * Main fragment hosting the terminal UI.
 * Integrates SSH client, transport layer, and connection dialog.
 */
public class TerminalFragment extends Fragment {

    private static final String TAG = "TerminalFragment";

    private View rootView;
    private TerminalView terminalView;
    private TerminalSession terminalSession;

    private Button connectBtn;
    private Button ctrlBtn;
    private Button escBtn;
    private Button tabBtn;
    private TextView statusText;
    private LinearLayout connectionOverlay;
    private LinearLayout bottomBar;

    private MainActivity mainActivity;
    private TerminalPrefs prefs;
    private CredentialManager credentialManager;

    // SSH connection state
    private SshClient sshClient;
    private UsbEcmTransport usbEcmTransport;
    private BleEthTransport bleEthTransport;
    private BleEthSocketFactory bleEthSocketFactory;
    private BluetoothService.BleEthDataCallback bleEthCallback;
    private boolean isSshConnected = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        prefs = new TerminalPrefs(requireContext());
        credentialManager = new CredentialManager(requireContext());
        credentialManager.migrateFromTerminalPrefs(requireContext());
        initViews(view);
        initTerminal();
        setupListeners();
        updateConnectionState();

        return view;
    }

    private void initViews(View view) {
        rootView = view;
        terminalView = view.findViewById(R.id.terminal_view);
        connectBtn = view.findViewById(R.id.terminal_connect_btn);
        ctrlBtn = view.findViewById(R.id.terminal_ctrl_btn);
        escBtn = view.findViewById(R.id.terminal_esc_btn);
        tabBtn = view.findViewById(R.id.terminal_tab_btn);
        statusText = view.findViewById(R.id.terminal_status);
        connectionOverlay = view.findViewById(R.id.terminal_connection_overlay);
        bottomBar = view.findViewById(R.id.terminal_bottom_bar);
    }

    private void initTerminal() {
        terminalSession = new TerminalSession(
                prefs.getTerminalRows(),
                prefs.getTerminalCols(),
                prefs.getScrollbackSize()
        );
        terminalView.setTerminalSession(terminalSession);
        terminalView.setFontSize(prefs.getFontSize());
    }

    private void setupListeners() {
        connectBtn.setOnClickListener(v -> {
            Log.d(TAG, "TerminalFragment connectBtn clicked, isSshConnected=" + isSshConnected);
            if (isSshConnected) {
                disconnect();
            } else {
                showConnectionDialog();
            }
        });

        ctrlBtn.setOnClickListener(v -> {
            if (terminalView != null) {
                terminalView.showKeyboard();
            }
        });

        escBtn.setOnClickListener(v -> {
            if (terminalView != null) {
                terminalView.sendSpecialKey("Esc");
                terminalView.showKeyboard();
            }
        });

        tabBtn.setOnClickListener(v -> {
            if (terminalView != null) {
                terminalView.sendSpecialKey("Tab");
                terminalView.showKeyboard();
            }
        });

        terminalView.setOnClickListener(v -> {
            if (isSshConnected && terminalView != null) {
                terminalView.showKeyboard();
            }
        });

        // Handle IME insets to keep bottom bar above the keyboard
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            if (imeInsets.bottom > 0) {
                // IME is visible, add bottom padding to push bottom bar above IME
                rootView.setPadding(0, 0, 0, imeInsets.bottom);
            } else {
                // IME is hidden, remove padding
                rootView.setPadding(0, 0, 0, 0);
            }
            return insets;
        });
    }

    private void updateConnectionState() {
        if (isSshConnected) {
            statusText.setText(R.string.terminal_connected);
            connectBtn.setText(R.string.terminal_disconnect);
            connectionOverlay.setVisibility(View.GONE);
        } else {
            statusText.setText(R.string.terminal_disconnected);
            connectBtn.setText(R.string.terminal_connect);
            connectionOverlay.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Show SSH connection dialog with device list and transport selection.
     */
    private void showConnectionDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.terminal_connection_dialog, null);

        // Bind UI elements
        RadioGroup transportGroup = dialogView.findViewById(R.id.terminal_transport_group);
        RadioButton usbRadio = dialogView.findViewById(R.id.transport_usb);
        RadioButton bleRadio = dialogView.findViewById(R.id.transport_ble);
        androidx.recyclerview.widget.RecyclerView deviceList = dialogView.findViewById(R.id.device_list);
        TextView emptyText = dialogView.findViewById(R.id.device_empty_text);

        // Load profiles
        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        DeviceAdapter adapter = new DeviceAdapter(profiles);

        if (profiles.isEmpty()) {
            // Show empty state
            deviceList.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            // Setup RecyclerView
            deviceList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
            deviceList.setAdapter(adapter);

            // Pre-select active profile
            CredentialProfile activeProfile = credentialManager.getActiveProfile();
            if (activeProfile != null) {
                for (int i = 0; i < profiles.size(); i++) {
                    if (profiles.get(i).getId().equals(activeProfile.getId())) {
                        adapter.setSelectedPosition(i);
                        break;
                    }
                }
            }
        }

        // Check transport availability and set defaults
        boolean usbAvailable = isUsbEcmAvailable();
        boolean bleAvailable = isBleAvailable();

        if (usbAvailable && !bleAvailable) {
            transportGroup.check(R.id.transport_usb);
            bleRadio.setEnabled(false);
        } else if (bleAvailable && !usbAvailable) {
            transportGroup.check(R.id.transport_ble);
            usbRadio.setEnabled(false);
        } else if (bleAvailable) {
            transportGroup.check(R.id.transport_ble);
        } else if (!usbAvailable && !bleAvailable) {
            Toast.makeText(getContext(), R.string.terminal_disconnected_hint, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.terminal_connect, (dialog, which) -> {
                    if (profiles.isEmpty()) {
                        Toast.makeText(getContext(), R.string.terminal_no_devices, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int selectedPos = adapter.getSelectedPosition();
                    if (selectedPos < 0 || selectedPos >= profiles.size()) {
                        Toast.makeText(getContext(), R.string.terminal_select_device, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CredentialProfile profile = profiles.get(selectedPos);
                    String host = profile.getHost();
                    int finalPort = profile.getPort();
                    String username = profile.getUsername();
                    String password = profile.getPassword();

                    if (host == null || host.isEmpty() || username == null || username.isEmpty()) {
                        Toast.makeText(getContext(), R.string.terminal_host_username_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean useUsb = transportGroup.getCheckedRadioButtonId() == R.id.transport_usb;

                    Log.d(TAG, "Dialog positive: host=" + host + " port=" + finalPort
                            + " user=" + username + " useUsb=" + useUsb);
                    connect(host, finalPort, username, password, useUsb);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Adapter for device selection list in connection dialog.
     */
    private class DeviceAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

        private final List<CredentialProfile> profiles;
        private int selectedPosition = 0;

        DeviceAdapter(List<CredentialProfile> profiles) {
            this.profiles = profiles;
        }

        void setSelectedPosition(int position) {
            int oldPosition = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(oldPosition);
            notifyItemChanged(position);
        }

        int getSelectedPosition() {
            return selectedPosition;
        }

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dialog_device, parent, false);
            return new DeviceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            CredentialProfile profile = profiles.get(position);
            holder.nameText.setText(profile.getDisplayLabel());
            holder.detailsText.setText(profile.getShortDescription());
            holder.radioButton.setChecked(position == selectedPosition);

            // Highlight selected item
            if (position == selectedPosition) {
                holder.cardView.setStrokeColor(requireContext().getResources().getColor(R.color.text_primary));
                holder.cardView.setStrokeWidth(2);
            } else {
                holder.cardView.setStrokeColor(requireContext().getResources().getColor(R.color.divider));
                holder.cardView.setStrokeWidth(1);
            }

            holder.itemView.setOnClickListener(v -> {
                setSelectedPosition(holder.getBindingAdapterPosition());
            });
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        class DeviceViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView cardView;
            RadioButton radioButton;
            TextView nameText;
            TextView detailsText;

            DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.device_card);
                radioButton = itemView.findViewById(R.id.device_radio);
                nameText = itemView.findViewById(R.id.device_name);
                detailsText = itemView.findViewById(R.id.device_details);
            }
        }
    }

    /**
     * Connect to SSH via the selected transport.
     */
    private void connect(String host, int port, String username, String password, boolean useUsb) {
        Log.d(TAG, "connect called: host=" + host + " port=" + port + " useUsb=" + useUsb);
        statusText.setText(R.string.terminal_connecting);

        if (useUsb) {
            connectUsbEcm(host, port, username, password);
        } else {
            connectBleEth(host, port, username, password);
        }
    }

    /**
     * Connect via USB ECM transport (direct socket).
     */
    private void connectUsbEcm(String host, int port, String username, String password) {
        usbEcmTransport = new UsbEcmTransport();

        // Set up transport listener BEFORE connect so the read thread can deliver data.
        usbEcmTransport.setListener(new TransportAdapter.Listener() {
            @Override
            public void onDataReceived(byte[] data, int len) {
                if (sshClient != null) {
                    sshClient.onDataReceivedFromTransport(data, len);
                }
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    isSshConnected = false;
                    statusText.setText(R.string.terminal_disconnected);
                    connectBtn.setText(R.string.terminal_connect);
                    connectionOverlay.setVisibility(View.VISIBLE);
                    terminalView.postInvalidate();
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "BLE-Eth SSH error: " + message);
                mainHandler.post(() -> {
                    statusText.setText(getString(R.string.terminal_connection_failed) + ": " + message);
                    isSshConnected = false;
                    updateConnectionState();
                });
            }
        });

        // First establish TCP connection
        new Thread(() -> {
            usbEcmTransport.connect(host, port, 15000);

            if (!usbEcmTransport.isConnected()) {
                mainHandler.post(() -> {
                    statusText.setText(getString(R.string.terminal_connection_failed) + ": USB ECM");
                    connectionOverlay.setVisibility(View.VISIBLE);
                });
                return;
            }

            // Then establish SSH session over the TCP connection
            // Use the actual host from the dialog, not from prefs.
            runSshSession(host, port, username, password, usbEcmTransport);
        }).start();
    }

    /**
     * Connect via BLE-Eth transport. Uses the app's BluetoothService.
     */
    private void connectBleEth(String host, int port, String username, String password) {
        Log.d(TAG, "connectBleEth called: host=" + host + " port=" + port);
        if (mainActivity == null) {
            Log.e(TAG, "connectBleEth: mainActivity is null");
            return;
        }
        BluetoothService bluetoothService = mainActivity.getBluetoothService();
        Log.d(TAG, "connectBleEth: bluetoothService=" + (bluetoothService != null)
                + " isConnected=" + (bluetoothService != null && bluetoothService.isConnected()));
        if (bluetoothService == null || !bluetoothService.isConnected()) {
            mainHandler.post(() -> {
                statusText.setText(R.string.terminal_connection_failed + ": BLE not connected");
                connectionOverlay.setVisibility(View.VISIBLE);
            });
            return;
        }

        // Clean up any previous BLE-Eth connection
        if (bleEthCallback != null) {
            bluetoothService.removeBleEthCallback(bleEthCallback);
            bleEthCallback = null;
        }
        if (bleEthTransport != null) {
            bleEthTransport.disconnect();
            bleEthTransport = null;
        }
        if (bleEthSocketFactory != null) {
            bleEthSocketFactory = null;
        }

        // Send DISCONNECT for all possible connection slots (0-5) to clean up
        // any stale firmware state. The firmware's TCP tunnel slot may be stuck
        // from a previous stalled session, causing new CONNECT to fail.
        for (int cid = 0; cid <= 5; cid++) {
            byte[] cleanupFrame = buildDisconnectFrame(cid);
            Log.d(TAG, "connectBleEth: sending cleanup DISCONNECT for connId=" + cid
                    + " frame=" + bytesToHex(cleanupFrame));
            bluetoothService.writeBleEthData(cleanupFrame);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        Log.d(TAG, "connectBleEth: waiting 5000ms for full cleanup");
        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        Log.d(TAG, "connectBleEth: cleanup complete, creating new BleEthTransport");

        bleEthTransport = new BleEthTransport(bluetoothService::writeBleEthData);
        Log.d(TAG, "connectBleEth: BleEthTransport created");

        // Register for incoming BLE-Eth data
        bleEthCallback = data -> {
            Log.d(TAG, "BLE-Eth callback: received " + data.length + " bytes");
            bleEthTransport.handleIncomingData(data);
        };
        Log.d(TAG, "connectBleEth: registering BLE-Eth callback with BluetoothService");
        bluetoothService.addBleEthCallback(bleEthCallback);
        Log.d(TAG, "connectBleEth: callback registered, starting connect thread");

        // Create SocketFactory that bridges JSch to BleEthTransport.
        // Pass the real target host/port so connectTunnel() sends the correct CONNECT frame.
        bleEthSocketFactory = new BleEthSocketFactory(bleEthTransport, host, port);
        Log.d(TAG, "connectBleEth: SocketFactory created, starting connect thread");

        // Start SSH session on background thread (SocketFactory handles BLE-Eth connect)
        new Thread(() -> {
            Log.d(TAG, "BLE-Eth SSH connect thread started");
            runSshSessionWithSocketFactory(host, port, username, password, bleEthTransport, bleEthSocketFactory);
            Log.d(TAG, "BLE-Eth SSH connect thread finished");
        }).start();
    }

    /**
     * Run the SSH session with a custom SocketFactory (for BLE-Eth).
     */
    private void runSshSessionWithSocketFactory(String host, int port, String username, String password,
                                                 TransportAdapter transport,
                                                 com.jcraft.jsch.SocketFactory socketFactory) {
        sshClient = new SshClient(host, port, username, password, transport, socketFactory);
        sshClient.setListener(new SshClient.Listener() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    isSshConnected = true;
                    statusText.setText(R.string.terminal_connected);
                    connectBtn.setText(R.string.terminal_disconnect);
                    connectionOverlay.setVisibility(View.GONE);
                    terminalView.postDelayed(() -> {
                        if (isSshConnected && terminalView != null) {
                            terminalView.showKeyboard();
                        }
                    }, 150);
                });
                // Start the shell channel
                sshClient.startShell(terminalSession);
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    isSshConnected = false;
                    statusText.setText(R.string.terminal_disconnected);
                    connectBtn.setText(R.string.terminal_connect);
                    connectionOverlay.setVisibility(View.VISIBLE);
                    terminalView.postInvalidate();
                });
            }

            @Override
            public void onDataReceived(byte[] data, int len) {
                terminalSession.append(data, len);
                mainHandler.post(() -> terminalView.invalidate());
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "BLE-Eth SSH error: " + message);
                mainHandler.post(() -> {
                    statusText.setText(getString(R.string.terminal_connection_failed) + ": " + message);
                    isSshConnected = false;
                    updateConnectionState();
                });
            }
        });

        sshClient.connect();
    }

    /**
     * Run the SSH session over an established transport (USB ECM - direct socket).
     */
    private void runSshSession(String host, int port, String username, String password, TransportAdapter transport) {
        sshClient = new SshClient(host, port, username, password, transport);
        sshClient.setListener(new SshClient.Listener() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    isSshConnected = true;
                    statusText.setText(R.string.terminal_connected);
                    connectBtn.setText(R.string.terminal_disconnect);
                    connectionOverlay.setVisibility(View.GONE);
                    terminalView.postDelayed(() -> {
                        if (isSshConnected && terminalView != null) {
                            terminalView.showKeyboard();
                        }
                    }, 150);
                });
                // Start the shell channel
                sshClient.startShell(terminalSession);
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    isSshConnected = false;
                    statusText.setText(R.string.terminal_disconnected);
                    connectBtn.setText(R.string.terminal_connect);
                    connectionOverlay.setVisibility(View.VISIBLE);
                    terminalView.postInvalidate();
                });
            }

            @Override
            public void onDataReceived(byte[] data, int len) {
                terminalSession.append(data, len);
                mainHandler.post(() -> terminalView.invalidate());
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "BLE-Eth SSH error: " + message);
                mainHandler.post(() -> {
                    statusText.setText(getString(R.string.terminal_connection_failed) + ": " + message);
                    isSshConnected = false;
                    updateConnectionState();
                });
            }
        });

        sshClient.connect();
    }

    /**
     * Disconnect the current SSH session.
     */
    private void disconnect() {
        if (sshClient != null) {
            sshClient.disconnect();
            sshClient = null;
        }
        // Clean up BLE-Eth callback
        if (bleEthCallback != null && mainActivity != null) {
            BluetoothService bs = mainActivity.getBluetoothService();
            if (bs != null) {
                bs.removeBleEthCallback(bleEthCallback);
            }
            bleEthCallback = null;
        }
        bleEthTransport = null;
        bleEthSocketFactory = null;
        usbEcmTransport = null;
        isSshConnected = false;
        updateConnectionState();
    }

    /**
     * Build a raw DISCONNECT BLE-Eth frame for a given connection ID.
     */
    private static byte[] buildDisconnectFrame(int connId) {
        byte[] frame = new byte[7];
        frame[0] = 0x57;
        frame[1] = (byte) 0xAB;
        frame[2] = 0x00;  // addr
        frame[3] = 0x12;  // CMD_DISCONNECT
        frame[4] = 0x01;  // payload length
        frame[5] = (byte) connId;
        int checksum = 0;
        for (int i = 0; i < 6; i++) {
            checksum += frame[i] & 0xFF;
        }
        frame[6] = (byte) (checksum & 0xFF);
        return frame;
    }

    /**
     * Convert byte array to hex string for logging.
     */
    private static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    private boolean isUsbEcmAvailable() {
        if (mainActivity == null) return false;
        ConnectionManager cm = mainActivity.getConnectionManager();
        return cm != null && cm.isConnected();
    }

    private boolean isBleAvailable() {
        if (mainActivity == null) return false;
        BluetoothService bluetoothService = mainActivity.getBluetoothService();
        return bluetoothService != null && bluetoothService.isConnected();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectionState();
    }

    @Override
    public void onDestroyView() {
        disconnect();
        super.onDestroyView();
    }
}
