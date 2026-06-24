package com.openterface.terminal;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.Button;
import android.widget.ImageView;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.SettingsActivity;

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
     * Show SSH connection dialog with profile dropdown and transport selection.
     */
    private void showConnectionDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.terminal_connection_dialog, null);

        // Bind UI elements
        RadioGroup transportGroup = dialogView.findViewById(R.id.terminal_transport_group);
        RadioButton usbRadio = dialogView.findViewById(R.id.transport_usb);
        RadioButton bleRadio = dialogView.findViewById(R.id.transport_ble);
        LinearLayout deviceListContainer = dialogView.findViewById(R.id.device_list_container);
        TextView emptyText = dialogView.findViewById(R.id.device_empty_text);
        MaterialButton addProfileBtn = dialogView.findViewById(R.id.add_profile_button);

        // Load profiles
        final List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        final int[] selectedProfileIndex = {-1};

        // Resolve theme colors for card styling
        final TypedValue primaryTypedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, primaryTypedValue, true);
        final int themePrimary = primaryTypedValue.data;

        if (profiles.isEmpty()) {
            // Show empty state with Add Profile button
            deviceListContainer.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            addProfileBtn.setVisibility(View.VISIBLE);
            addProfileBtn.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_TAB_INDEX, SettingsActivity.TAB_CREDENTIALS);
                requireContext().startActivity(intent);
            });
        } else {
            final LayoutInflater cardInflater = LayoutInflater.from(getContext());

            for (int i = 0; i < profiles.size(); i++) {
                final CredentialProfile profile = profiles.get(i);
                final int index = i;

                View cardView = cardInflater.inflate(
                        R.layout.item_dialog_device, deviceListContainer, false);

                MaterialCardView card = cardView.findViewById(R.id.device_card);
                ImageView radioIndicator = cardView.findViewById(R.id.device_radio);
                TextView nameText = cardView.findViewById(R.id.device_name);
                TextView descText = cardView.findViewById(R.id.device_details);

                nameText.setText(profile.getDisplayLabel());
                descText.setText(profile.getShortDescription());

                // Apply initial (unselected) styling
                applyUnselectedCardStyle(card, radioIndicator, nameText, descText);

                // Pre-select active profile
                CredentialProfile activeProfile = credentialManager.getActiveProfile();
                boolean isActive = activeProfile != null
                        && profile.getId().equals(activeProfile.getId());
                if (isActive) {
                    selectedProfileIndex[0] = index;
                    applySelectedCardStyle(card, radioIndicator, nameText, descText, themePrimary);
                }

                card.setOnClickListener(v -> {
                    int prevIndex = selectedProfileIndex[0];
                    selectedProfileIndex[0] = index;

                    // Reset previously selected card
                    if (prevIndex >= 0 && prevIndex < deviceListContainer.getChildCount()) {
                        View prevChild = deviceListContainer.getChildAt(prevIndex);
                        if (prevChild instanceof MaterialCardView) {
                            MaterialCardView prevCard = (MaterialCardView) prevChild;
                            ImageView prevRadio = prevCard.findViewById(R.id.device_radio);
                            TextView prevName = prevCard.findViewById(R.id.device_name);
                            TextView prevDesc = prevCard.findViewById(R.id.device_details);
                            applyUnselectedCardStyle(prevCard, prevRadio, prevName, prevDesc);
                        }
                    }

                    // Apply selected style to this card
                    applySelectedCardStyle(card, radioIndicator, nameText, descText, themePrimary);
                });

                deviceListContainer.addView(cardView);
            }

            addProfileBtn.setVisibility(View.GONE);
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

                    if (selectedProfileIndex[0] < 0 || selectedProfileIndex[0] >= profiles.size()) {
                        Toast.makeText(getContext(), R.string.terminal_select_device, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CredentialProfile profile = profiles.get(selectedProfileIndex[0]);
                    String host = profile.getHost();
                    int finalPort = profile.getPort();
                    String username = profile.getUsername();

                    if (host == null || host.isEmpty() || username == null || username.isEmpty()) {
                        Toast.makeText(getContext(), R.string.terminal_host_username_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean useUsb = transportGroup.getCheckedRadioButtonId() == R.id.transport_usb;

                    // Save selected profile as active
                    credentialManager.setActiveProfileId(profile.getId());

                    Log.d(TAG, "Dialog positive: authType=" + profile.getAuthType() + " useUsb=" + useUsb);
                    connect(profile, useUsb);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Apply selected card style: theme primary border + bullseye radio indicator.
     * Uses MaterialCardView API so stroke/color are set via properties.
     */
    private void applySelectedCardStyle(MaterialCardView card, ImageView radio,
                                         TextView nameText, TextView descText, int themePrimary) {
        // Card: soft light-gray stroke via MaterialCardView stroke API
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics());
        card.setStrokeWidth(strokeWidth);
        card.setStrokeColor(0xFFBDBDBD);
        card.setCardBackgroundColor(getResources().getColor(R.color.terminal_toolbar_background));

        // Radio indicator: ring with inner dot (bullseye style)
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        int radioSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        ringBg.setSize(radioSize, radioSize);
        ringBg.setColor(android.graphics.Color.TRANSPARENT);
        ringBg.setStroke(
                (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                themePrimary);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(themePrimary);

        int gap = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        LayerDrawable radioBg = new LayerDrawable(
                new android.graphics.drawable.Drawable[]{ringBg, dotBg});
        radioBg.setLayerInset(1, gap, gap, gap, gap);
        radio.setImageDrawable(radioBg);

        nameText.setTextColor(getResources().getColor(R.color.text_primary));
        descText.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    /**
     * Apply unselected card style: no stroke + hollow gray radio indicator.
     */
    private void applyUnselectedCardStyle(MaterialCardView card, ImageView radio,
                                           TextView nameText, TextView descText) {
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(getResources().getColor(R.color.terminal_toolbar_background));

        GradientDrawable radioBg = new GradientDrawable();
        radioBg.setShape(GradientDrawable.OVAL);
        int radioSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        radioBg.setSize(radioSize, radioSize);
        radioBg.setStroke(
                (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                getResources().getColor(R.color.gray_400));
        radio.setImageDrawable(radioBg);

        nameText.setTextColor(getResources().getColor(R.color.text_primary));
        descText.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    /**
     * Connect to SSH via the selected transport.
     */
    private void connect(CredentialProfile profile, boolean useUsb) {
        Log.d(TAG, "connect called: authType=" + profile.getAuthType() + " useUsb=" + useUsb);
        statusText.setText(R.string.terminal_connecting);

        if (useUsb) {
            connectUsbEcm(profile);
        } else {
            connectBleEth(profile);
        }
    }

    /**
     * Connect via USB ECM transport (direct socket).
     */
    private void connectUsbEcm(CredentialProfile profile) {
        final String host = profile.getHost();
        final int port = profile.getPort();
        final String username = profile.getUsername();
        final String password = profile.getPassword();
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
                Log.e(TAG, "SSH error: " + message);
                mainHandler.post(() -> {
                    String displayMessage;
                    if (message.contains("AUTH_FAILED")) {
                        displayMessage = getString(R.string.terminal_auth_failed);
                    } else {
                        displayMessage = getFriendlyErrorMessage(message);
                    }
                    statusText.setText(displayMessage);
                    Toast.makeText(getContext(), displayMessage, Toast.LENGTH_LONG).show();
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
            runSshSession(profile, usbEcmTransport);
        }).start();
    }

    /**
     * Connect via BLE-Eth transport. Uses the app's BluetoothService.
     */
    private void connectBleEth(CredentialProfile profile) {
        final String host = profile.getHost();
        final int port = profile.getPort();
        Log.d(TAG, "connectBleEth called");
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
            Log.d(TAG, "connectBleEth: sending cleanup DISCONNECT for connId=" + cid);
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
            runSshSessionWithSocketFactory(profile, bleEthTransport, bleEthSocketFactory);
            Log.d(TAG, "BLE-Eth SSH connect thread finished");
        }).start();
    }

    /**
     * Run the SSH session with a custom SocketFactory (for BLE-Eth).
     */
    private void runSshSessionWithSocketFactory(CredentialProfile profile,
                                                 TransportAdapter transport,
                                                 com.jcraft.jsch.SocketFactory socketFactory) {
        sshClient = new SshClient(profile, transport, socketFactory);
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
                Log.e(TAG, "SSH error: " + message);
                mainHandler.post(() -> {
                    String displayMessage;
                    if (message.contains("AUTH_FAILED")) {
                        displayMessage = getString(R.string.terminal_auth_failed);
                    } else {
                        displayMessage = getFriendlyErrorMessage(message);
                    }
                    statusText.setText(displayMessage);
                    Toast.makeText(getContext(), displayMessage, Toast.LENGTH_LONG).show();
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
    private void runSshSession(CredentialProfile profile, TransportAdapter transport) {
        sshClient = new SshClient(profile, transport);
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
                Log.e(TAG, "SSH error: " + message);
                mainHandler.post(() -> {
                    String displayMessage;
                    if (message.contains("AUTH_FAILED")) {
                        displayMessage = getString(R.string.terminal_auth_failed);
                    } else {
                        displayMessage = getFriendlyErrorMessage(message);
                    }
                    statusText.setText(displayMessage);
                    Toast.makeText(getContext(), displayMessage, Toast.LENGTH_LONG).show();
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

    /**
     * Convert technical error messages to user-friendly ones.
     * @param errorMessage The raw error message from SSH client
     * @return A user-friendly error message
     */
    private String getFriendlyErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return getString(R.string.terminal_connection_failed);
        }

        String lowerError = errorMessage.toLowerCase();

        // Connection refused - target host is reachable but not accepting connections
        if (lowerError.contains("connection refused") || lowerError.contains("refused")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_connection_refused);
        }

        // Connection timeout - network issue or host not responding
        if (lowerError.contains("timeout") || lowerError.contains("timed out")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_connection_timeout);
        }

        // Unknown host - DNS resolution failed
        if (lowerError.contains("unknownhost") || lowerError.contains("unknown host") || lowerError.contains("resolve")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_unknown_host);
        }

        // No route to host - network routing issue
        if (lowerError.contains("no route") || lowerError.contains("network is unreachable")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_no_route);
        }

        // For other errors, show a generic message
        return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_generic);
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
