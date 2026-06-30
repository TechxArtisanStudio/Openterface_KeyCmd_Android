package com.openterface.terminal;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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
    public static final String ARG_DEMO_TRANSPORT = "demo_transport";
    public static final String DEMO_USB = "usb";
    public static final String DEMO_BLE = "ble";

    public static TerminalFragment newInstance(@Nullable String demoTransport) {
        TerminalFragment fragment = new TerminalFragment();
        if (demoTransport != null) {
            Bundle args = new Bundle();
            args.putString(ARG_DEMO_TRANSPORT, demoTransport);
            fragment.setArguments(args);
        }
        return fragment;
    }

    private View rootView;
    private TerminalView terminalView;
    private TerminalSession terminalSession;

    private Button connectBtn;
    private Button ctrlBtn;
    private Button escBtn;
    private Button tabBtn;
    private TextView statusText;
    private TextView transportBadge;
    private TextView hostLabel;
    private LinearLayout connectionOverlay;
    private LinearLayout bottomBar;
    private Button demoUsbBtn;
    private Button demoBleBtn;
    private LinearLayout demoButtonRow;

    private MainActivity mainActivity;
    private TerminalPrefs prefs;
    private CredentialManager credentialManager;
    private TerminalDemoController demoController;
    private boolean isDemoActive = false;
    @Nullable
    private TerminalDemoController.DemoTransport activeDemoTransport;
    @Nullable
    private String activeSessionHost;

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
        credentialManager.ensureDefaultKeyCmdProfile();
        initViews(view);
        initTerminal();
        setupListeners();
        updateConnectionState();
        maybeStartPendingDemo();

        return view;
    }

    private void maybeStartPendingDemo() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        String transport = args.getString(ARG_DEMO_TRANSPORT);
        if (transport == null) {
            return;
        }
        args.remove(ARG_DEMO_TRANSPORT);
        mainHandler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            if (DEMO_BLE.equalsIgnoreCase(transport)) {
                showConnectionDialog(TerminalDemoController.DemoTransport.BLE);
            } else if (DEMO_USB.equalsIgnoreCase(transport)) {
                showConnectionDialog(TerminalDemoController.DemoTransport.USB);
            } else {
                showConnectionDialog(TerminalDemoController.DemoTransport.BLE);
            }
        }, 350);
    }

    private void initViews(View view) {
        rootView = view;
        terminalView = view.findViewById(R.id.terminal_view);
        connectBtn = view.findViewById(R.id.terminal_connect_btn);
        ctrlBtn = view.findViewById(R.id.terminal_ctrl_btn);
        escBtn = view.findViewById(R.id.terminal_esc_btn);
        tabBtn = view.findViewById(R.id.terminal_tab_btn);
        statusText = view.findViewById(R.id.terminal_status);
        transportBadge = view.findViewById(R.id.terminal_transport_badge);
        hostLabel = view.findViewById(R.id.terminal_host_label);
        connectionOverlay = view.findViewById(R.id.terminal_connection_overlay);
        bottomBar = view.findViewById(R.id.terminal_bottom_bar);
        demoButtonRow = view.findViewById(R.id.terminal_empty_button_row);
        demoUsbBtn = view.findViewById(R.id.terminal_demo_usb_btn);
        demoBleBtn = view.findViewById(R.id.terminal_demo_ble_btn);
        demoController = new TerminalDemoController();
        applyEmptyStateButtonLayout();
    }

    private void applyEmptyStateButtonLayout() {
        if (demoButtonRow == null || demoBleBtn == null || demoUsbBtn == null) {
            return;
        }
        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int width = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_width);
        int height = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_gap);
        demoButtonRow.setOrientation(isLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout.LayoutParams bleParams = new LinearLayout.LayoutParams(width, height);
        demoBleBtn.setLayoutParams(bleParams);

        LinearLayout.LayoutParams usbParams = new LinearLayout.LayoutParams(width, height);
        if (isLandscape) {
            usbParams.setMarginStart(gap);
        } else {
            usbParams.topMargin = gap;
        }
        demoUsbBtn.setLayoutParams(usbParams);
    }

    private void initTerminal() {
        terminalSession = new TerminalSession(
                prefs.getTerminalRows(),
                prefs.getTerminalCols(),
                prefs.getScrollbackSize()
        );
        terminalView.setTerminalSession(terminalSession);
        terminalView.setFontSize(prefs.getFontSize());
        terminalView.setAutoFitFontSize(!prefs.hasFontSizeOverride());
    }

    private void setupListeners() {
        connectBtn.setOnClickListener(v -> {
            Log.d(TAG, "TerminalFragment connectBtn clicked, isSshConnected=" + isSshConnected
                    + " isDemoActive=" + isDemoActive);
            if (isSessionActive()) {
                if (isDemoActive) {
                    stopDemo();
                } else {
                    disconnect();
                }
            } else {
                showConnectChoiceDialog();
            }
        });

        if (demoUsbBtn != null) {
            demoUsbBtn.setOnClickListener(v ->
                    showConnectionDialog(TerminalDemoController.DemoTransport.USB));
        }
        if (demoBleBtn != null) {
            demoBleBtn.setOnClickListener(v ->
                    showConnectionDialog(TerminalDemoController.DemoTransport.BLE));
        }

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
            if (isSessionActive() && terminalView != null) {
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

    private boolean isSessionActive() {
        return isSshConnected || isDemoActive;
    }

    private void resetTerminalSession() {
        terminalSession = new TerminalSession(
                prefs.getTerminalRows(),
                prefs.getTerminalCols(),
                prefs.getScrollbackSize()
        );
        terminalView.setTerminalSession(terminalSession);
    }

    private void startDemo(@NonNull TerminalDemoController.DemoTransport transport) {
        startDemo(transport, TerminalDemoController.DEMO_HOST);
    }

    private void startDemo(@NonNull TerminalDemoController.DemoTransport transport, @NonNull String host) {
        if (getContext() == null || terminalSession == null || demoController == null) {
            return;
        }
        disconnect();
        resetTerminalSession();
        isDemoActive = true;
        activeDemoTransport = transport;
        activeSessionHost = host;
        updateConnectionState();
        statusText.setText(R.string.terminal_connecting);

        demoController.start(requireContext(), terminalSession, transport,
                new TerminalDemoController.Listener() {
                    @Override
                    public void onOutputAppended() {
                        if (terminalView != null) {
                            terminalView.invalidate();
                        }
                    }

                    @Override
                    public void onFinished() {
                        if (!isDemoActive) {
                            return;
                        }
                        statusText.setText(R.string.terminal_connected);
                        if (terminalView != null) {
                            terminalView.invalidate();
                        }
                    }
                });
    }

    private void stopDemo() {
        if (demoController != null) {
            demoController.stop();
        }
        if (!isDemoActive) {
            return;
        }
        isDemoActive = false;
        activeDemoTransport = null;
        activeSessionHost = null;
        resetTerminalSession();
        updateConnectionState();
    }

    private void updateConnectionState() {
        if (transportBadge != null) {
            if (isDemoActive && activeDemoTransport != null) {
                transportBadge.setVisibility(View.VISIBLE);
                if (activeDemoTransport == TerminalDemoController.DemoTransport.USB) {
                    transportBadge.setText(R.string.terminal_badge_usb);
                    transportBadge.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_usb_24, 0, 0, 0);
                } else {
                    transportBadge.setText(R.string.terminal_badge_ble);
                    transportBadge.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_bluetooth_24, 0, 0, 0);
                }
            } else if (isSshConnected) {
                transportBadge.setVisibility(View.GONE);
                transportBadge.setCompoundDrawables(null, null, null, null);
            } else {
                transportBadge.setVisibility(View.GONE);
                transportBadge.setCompoundDrawables(null, null, null, null);
            }
        }

        if (hostLabel != null) {
            if (isSessionActive()) {
                hostLabel.setVisibility(View.VISIBLE);
                hostLabel.setText(activeSessionHost != null
                        ? activeSessionHost
                        : TerminalDemoController.DEMO_HOST);
            } else {
                hostLabel.setVisibility(View.GONE);
            }
        }

        if (isSessionActive()) {
            statusText.setText(R.string.terminal_connected);
            connectBtn.setText(R.string.terminal_disconnect);
            connectionOverlay.setVisibility(View.GONE);
        } else {
            statusText.setText(R.string.terminal_disconnected);
            connectBtn.setText(R.string.terminal_connect);
            connectionOverlay.setVisibility(View.VISIBLE);
        }
    }

    /** Offer preview demo or real SSH when disconnected. */
    private void showConnectChoiceDialog() {
        showConnectionDialog(null);
    }

    /**
     * Show SSH connection dialog with transport selection and credentials.
     */
    private void showConnectionDialog() {
        showConnectionDialog(null);
    }

    private void showConnectionDialog(@Nullable TerminalDemoController.DemoTransport demoTransport) {
        if (getContext() == null) return;
        stopDemo();

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.terminal_connection_dialog, null);

        TextView dialogTitle = dialogView.findViewById(R.id.terminal_connection_dialog_title);
        TextView dialogSubtitle = dialogView.findViewById(R.id.terminal_connection_dialog_subtitle);
        RadioGroup transportGroup = dialogView.findViewById(R.id.terminal_transport_group);
        EditText profileNameInput = dialogView.findViewById(R.id.terminal_profile_name_input);
        EditText hostInput = dialogView.findViewById(R.id.terminal_host_input);
        EditText portInput = dialogView.findViewById(R.id.terminal_port_input);
        EditText usernameInput = dialogView.findViewById(R.id.terminal_username_input);
        EditText passwordInput = dialogView.findViewById(R.id.terminal_password_input);
        EditText privateKeyInput = dialogView.findViewById(R.id.terminal_private_key_input);
        EditText notesInput = dialogView.findViewById(R.id.terminal_notes_input);
        Spinner profileSpinner = dialogView.findViewById(R.id.terminal_profile_spinner);
        Spinner targetOsSpinner = dialogView.findViewById(R.id.terminal_target_os_spinner);
        Spinner authSpinner = dialogView.findViewById(R.id.terminal_auth_spinner);
        CheckBox saveProfileCheck = dialogView.findViewById(R.id.terminal_remember_credentials);

        if (demoTransport != null) {
            dialogTitle.setText(R.string.terminal_start_demo);
            dialogSubtitle.setText(R.string.terminal_profile_dialog_summary);
        }

        String[] targetOsValues = {"linux", "macos", "windows"};
        String[] targetOsLabels = {"Linux", "macOS", "Windows"};
        ArrayAdapter<String> targetOsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, targetOsLabels);
        targetOsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetOsSpinner.setAdapter(targetOsAdapter);

        String[] authValues = {"password", "private_key", "password_or_key"};
        String[] authLabels = {
                getString(R.string.terminal_auth_password),
                getString(R.string.terminal_auth_private_key),
                getString(R.string.terminal_auth_password_or_key)
        };
        ArrayAdapter<String> authAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, authLabels);
        authAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        authSpinner.setAdapter(authAdapter);

        // Build profile list for spinner
        List<CredentialProfile> profiles = credentialManager.getAllProfiles();
        List<String> profileLabels = new ArrayList<>();
        profileLabels.add(getString(R.string.credential_new_connection));
        CredentialProfile[] profileArray = new CredentialProfile[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            CredentialProfile p = profiles.get(i);
            profileArray[i] = p;
            profileLabels.add(p.getDisplayLabel());
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, profileLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(spinnerAdapter);

        // Pre-select active profile if present
        CredentialProfile activeProfile = credentialManager.getActiveProfile();
        int initialSelection = 0; // "New connection…"
        if (activeProfile != null) {
            for (int i = 0; i < profileArray.length; i++) {
                if (profileArray[i] != null && profileArray[i].getId().equals(activeProfile.getId())) {
                    initialSelection = i + 1;
                    break;
                }
            }
        }

        // Track which profile was selected (1-based index into profileLabels, 0 = new)
        final int[] selectedProfileIndex = {initialSelection};

        // Auto-fill fields from selected profile
        if (initialSelection > 0 && profileArray[initialSelection - 1] != null) {
            CredentialProfile ap = profileArray[initialSelection - 1];
            profileNameInput.setText(ap.getDisplayLabel());
            hostInput.setText(ap.getHost());
            portInput.setText(String.valueOf(ap.getPort()));
            usernameInput.setText(ap.getUsername());
            passwordInput.setText(ap.getPassword());
            privateKeyInput.setText(ap.getPrivateKey());
            notesInput.setText(ap.getNotes());
            targetOsSpinner.setSelection(indexOf(targetOsValues, ap.getTargetOs()));
            authSpinner.setSelection(indexOf(authValues, ap.getAuthMethod()));
        } else {
            // Fall back to legacy prefs for new connections
            profileNameInput.setText("KeyCmd default");
            hostInput.setText(CredentialManager.DEFAULT_KEYCMD_HOST);
            portInput.setText("22");
            usernameInput.setText(prefs.getLastUsername());
            passwordInput.setText(prefs.getLastPassword());
            targetOsSpinner.setSelection(0);
            authSpinner.setSelection(0);
        }

        profileSpinner.setSelection(initialSelection);
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedProfileIndex[0] = position;
                if (position > 0 && profileArray[position - 1] != null) {
                    CredentialProfile selected = profileArray[position - 1];
                    profileNameInput.setText(selected.getDisplayLabel());
                    hostInput.setText(selected.getHost());
                    portInput.setText(String.valueOf(selected.getPort()));
                    usernameInput.setText(selected.getUsername());
                    passwordInput.setText(selected.getPassword());
                    privateKeyInput.setText(selected.getPrivateKey());
                    notesInput.setText(selected.getNotes());
                    targetOsSpinner.setSelection(indexOf(targetOsValues, selected.getTargetOs()));
                    authSpinner.setSelection(indexOf(authValues, selected.getAuthMethod()));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedProfileIndex[0] = 0;
            }
        });

        if (demoTransport != null) {
            transportGroup.check(demoTransport == TerminalDemoController.DemoTransport.USB
                    ? R.id.transport_usb : R.id.transport_ble);
            dialogView.findViewById(R.id.transport_usb)
                    .setEnabled(demoTransport == TerminalDemoController.DemoTransport.USB);
            dialogView.findViewById(R.id.transport_ble)
                    .setEnabled(demoTransport == TerminalDemoController.DemoTransport.BLE);
        } else {
            // Check transport availability and set defaults
            boolean usbAvailable = isUsbEcmAvailable();
            boolean bleAvailable = isBleAvailable();

            if (usbAvailable && !bleAvailable) {
                transportGroup.check(R.id.transport_usb);
                dialogView.findViewById(R.id.transport_ble).setEnabled(false);
            } else if (bleAvailable && !usbAvailable) {
                transportGroup.check(R.id.transport_ble);
                dialogView.findViewById(R.id.transport_usb).setEnabled(false);
            } else if (bleAvailable) {
                // Both available — prefer BLE-Eth when BLE is already connected
                transportGroup.check(R.id.transport_ble);
            } else if (!usbAvailable && !bleAvailable) {
                transportGroup.check(R.id.transport_ble);
            }
        }

        int positiveText = demoTransport != null
                ? R.string.terminal_start_demo
                : R.string.terminal_connect;

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(positiveText, (dialog, which) -> {
                    String profileName = profileNameInput.getText().toString().trim();
                    String host = hostInput.getText().toString().trim();
                    String portStr = portInput.getText().toString().trim();
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    String privateKey = privateKeyInput.getText().toString();
                    String notes = notesInput.getText().toString();
                    String targetOs = targetOsValues[targetOsSpinner.getSelectedItemPosition()];
                    String authMethod = authValues[authSpinner.getSelectedItemPosition()];

                    if (host.isEmpty() || username.isEmpty()) {
                        Toast.makeText(getContext(), "Host and username are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int port = 22;
                    try {
                        if (!portStr.isEmpty()) {
                            port = Integer.parseInt(portStr);
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Invalid port number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final int finalPort = port;
                    boolean useUsb = transportGroup.getCheckedRadioButtonId() == R.id.transport_usb;
                    CredentialProfile profile = null;

                    int spIdx = selectedProfileIndex[0];
                    if (spIdx > 0 && profileArray[spIdx - 1] != null) {
                        profile = profileArray[spIdx - 1];
                    }
                    if (profile == null) {
                        profile = new CredentialProfile();
                    }
                    profile.setName(profileName.isEmpty() ? username + "@" + host : profileName);
                    profile.setHost(host);
                    profile.setPort(finalPort);
                    profile.setUsername(username);
                    profile.setPassword(password);
                    profile.setTargetOs(targetOs);
                    profile.setAuthMethod(authMethod);
                    profile.setPrivateKey(privateKey);
                    profile.setNotes(notes);

                    if (saveProfileCheck == null || saveProfileCheck.isChecked()) {
                        if (spIdx > 0) {
                            credentialManager.updateProfile(profile);
                        } else {
                            credentialManager.addProfile(profile);
                        }
                        credentialManager.setActiveProfileId(profile.getId());
                    }

                    if (demoTransport != null) {
                        statusText.setText(R.string.terminal_connecting);
                        hostLabel.setText(host);
                        startDemo(demoTransport, host);
                    } else {
                        Log.d(TAG, "Dialog positive: host=" + host + " port=" + finalPort
                                + " user=" + username + " useUsb=" + useUsb);
                        connect(host, finalPort, username, password, useUsb);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(String[] values, String value) {
        if (value == null) {
            return 0;
        }
        for (int i = 0; i < values.length; i++) {
            if (value.equals(values[i])) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Connect to SSH via the selected transport.
     */
    private void connect(String host, int port, String username, String password, boolean useUsb) {
        Log.d(TAG, "connect called: host=" + host + " port=" + port + " useUsb=" + useUsb);
        stopDemo();
        activeSessionHost = host;
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
        stopDemo();
        activeSessionHost = null;
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
        stopDemo();
        disconnect();
        super.onDestroyView();
    }
}
