package com.openterface.terminal;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.openterface.keymod.AppLocaleManager;
import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;

import android.util.Log;

/**
 * Standalone Activity for exporting SSH public key to a configured server.
 * Shows server info, SSH keys location, public key file name, and an editable snippet.
 * The snippet keeps $1, $2, $3 as visible placeholders; they are substituted at execution time.
 */
public class ExportPublicKeyActivity extends AppCompatActivity {

    private static final String TAG = "ExportPublicKeyActivity";

    public static final String EXTRA_PUBLIC_KEY = "extra_public_key";
    public static final String EXTRA_PROFILE_ID = "extra_profile_id";

    private static final String DEFAULT_SNIPPET =
            "if test ! -e $1;\n" +
            "then mkdir $1;\n" +
            "     chmod 700 $1;\n" +
            "fi;\n" +
            "if test ! -e \"$1/$2\";\n" +
            "then touch \"$1/$2\";\n" +
            "     chmod 600 \"$1/$2\";\n" +
            "fi;\n" +
            // Pre-check: ensure file ends with \n so key won't stack on last line.
            // Post-append: add trailing \n so next key also won't stack.
            "if [ -s \"$1/$2\" ] && [ \"$(tail -c1 \"$1/$2\" | wc -l)\" -eq 0 ]; then\n" +
            "     echo '' >> \"$1/$2\";\n" +
            "fi;\n" +
            "echo '$3' | base64 -d >> \"$1/$2\"; echo '' >> \"$1/$2\";";

    private String publicKey;
    private CredentialProfile profile;

    private TextInputEditText sshKeysLocationInput;
    private TextInputEditText publicKeyFileInput;
    private EditText snippetText;
    private MaterialButton exportButton;
    private TextView serverName;
    private TextView serverTags;
    private MaterialCardView serverInfoCard;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppLocaleManager.applyPersistedLocales(this);
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_export_public_key);
        setupWindowInsets();
        applyNonImmersiveSystemBars();

        publicKey = getIntent().getStringExtra(EXTRA_PUBLIC_KEY);
        String profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);

        // Load profile if ID is provided
        if (profileId != null) {
            CredentialManager credentialManager = new CredentialManager(this);
            profile = credentialManager.getProfile(profileId);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        setupViews();
    }

    private void setupViews() {
        sshKeysLocationInput = findViewById(R.id.ssh_keys_location_input);
        publicKeyFileInput = findViewById(R.id.public_key_file_input);
        snippetText = findViewById(R.id.snippet_text);
        exportButton = findViewById(R.id.export_button);
        serverName = findViewById(R.id.server_name);
        serverTags = findViewById(R.id.server_tags);
        serverInfoCard = findViewById(R.id.server_info_card);

        // Setup server info
        if (profile != null) {
            serverName.setText(profile.getDisplayLabel());
            String tags = TextUtils.join(", ", profile.getTags());
            if (tags.isEmpty()) {
                tags = profile.getHost() != null ? profile.getHost() : "";
            }
            serverTags.setText(tags);
            serverInfoCard.setVisibility(View.VISIBLE);

            // Update icon based on target OS (default: generic server icon)
            android.widget.ImageView serverIcon = findViewById(R.id.server_icon);
            if (serverIcon != null) {
                String targetOs = profile.getTargetOs();
                if ("macos".equals(targetOs)) {
                    serverIcon.setImageResource(R.drawable.ic_os_macos);
                } else if ("windows".equals(targetOs)) {
                    serverIcon.setImageResource(R.drawable.ic_os_windows);
                } else {
                    // linux or any other value: use generic server icon
                    serverIcon.setImageResource(R.drawable.ic_generic_server_24);
                }
            }
        } else {
            serverInfoCard.setVisibility(View.GONE);
        }

        // Pre-fill snippet with default template (keeps $1, $2, $3 as visible placeholders)
        snippetText.setText(DEFAULT_SNIPPET);

        // Export button click
        exportButton.setOnClickListener(v -> {
            if (publicKey == null || publicKey.isEmpty()) {
                Toast.makeText(this, R.string.credential_public_key_not_available, Toast.LENGTH_SHORT).show();
                return;
            }
            if (profile == null) {
                Toast.makeText(this, "No server configured", Toast.LENGTH_SHORT).show();
                return;
            }
            performExport();
        });
    }

    /**
     * Substitute $1, $2, $3 in the user-edited snippet with actual values
     * and send the resulting command to the remote server.
     *
     * Three paths:
     *  1) Active SSH session (terminal connected): execute via ChannelExec on the
     *     existing session — works for both BLE-Eth and USB ECM.
     *  2) No active session, last connection was USB ECM: create a new SSH connection
     *     via direct TCP (works because USB ECM provides a virtual network interface).
     *  3) No active session, last connection was BLE-Eth: create a temporary BLE-Eth
     *     transport + SocketFactory, push the key, then tear down the transport.
     */
    private void performExport() {
        String sshKeysLocation = sshKeysLocationInput.getText() != null
                ? sshKeysLocationInput.getText().toString().trim() : ".ssh";
        String publicKeyFile = publicKeyFileInput.getText() != null
                ? publicKeyFileInput.getText().toString().trim() : "authorized_keys";

        String host = profile.getHost();
        int port = profile.getPort();
        String username = profile.getUsername();
        String password = profile.getPassword();

        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this,
                    getString(R.string.credential_push_key_failed, "Host, username and password are required"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Build the final command by substituting placeholders in the user-edited snippet
        String snippet = snippetText.getText() != null ? snippetText.getText().toString() : DEFAULT_SNIPPET;
        // Strip the comment field from the public key (OpenSSH format: "type base64-key comment")
        String cleanPublicKey = stripPublicKeyComment(publicKey);
        // Encode the public key as base64 so it becomes a single safe line (no newlines, no
        // special characters). Without this, multi-line keys break the echo command:
        //   echo 'ssh-ed25519 AAAA...\ncomment' >> file
        // The newline inside the single-quoted string terminates the echo, and subsequent
        // lines are interpreted as shell commands — causing the script to be written to the file.
        String base64PublicKey = android.util.Base64.encodeToString(
                cleanPublicKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
        String command = snippet.replace("$1", sshKeysLocation)
                                .replace("$2", publicKeyFile)
                                .replace("$3", base64PublicKey);

        // Show progress text
        exportButton.setText(R.string.credential_push_key_connecting);

        // Path 1: Active SSH session (terminal is connected)
        SshClient activeClient = TerminalFragment.getActiveSshClient();
        if (activeClient != null && activeClient.isConnected()) {
            executeViaActiveSession(activeClient, command);
            return;
        }

        // Check actual connection state (not remembered type)
        BluetoothService bluetoothService = MainActivity.getStaticBluetoothService();
        boolean bleConnected = bluetoothService != null && bluetoothService.isConnected();
        ConnectionManager connectionManager = MainActivity.getStaticConnectionManager();
        boolean usbConnected = connectionManager != null && connectionManager.isConnected();

        // Path 2: BLE-Eth — create a temporary transport for the push
        // (BLE is the primary connection for this app; prefer it over USB when both are available)
        if (bleConnected) {
            Log.i(TAG, "No active session, BLE connected — creating temporary BLE-Eth transport");
            executeViaBleEthTransport(bluetoothService, host, port, username, password, command);
            return;
        }

        // Path 3: USB ECM — direct TCP (no SocketFactory needed)
        if (usbConnected) {
            Log.i(TAG, "No active session, USB connected — using direct TCP");
            SshKeyPusher.executeCommand(host, port, username, password, command, null,
                    createProgressListener());
            return;
        }

        // Nothing available
        Log.w(TAG, "No transport available for push (BLE not connected, no USB)");
        Toast.makeText(this,
                getString(R.string.credential_push_key_failed, "No connection available. Connect to the device first."),
                Toast.LENGTH_LONG).show();
        exportButton.setEnabled(true);
        exportButton.setText(R.string.credential_export);
    }

    /**
     * Execute the push command through the already-connected SSH session.
     * This is the only viable path for BLE-Eth (single-connection transport).
     */
    private void executeViaActiveSession(SshClient client, String command) {
        new Thread(() -> {
            try {
                runOnUiThread(() -> exportButton.setText("Executing command…"));
                Log.i(TAG, "Executing push via active SSH session");
                int exitCode = client.executeCommand(command);
                if (exitCode == 0) {
                    Log.i(TAG, "Push command succeeded");
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                R.string.credential_push_key_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    Log.w(TAG, "Push command exited with " + exitCode);
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                getString(R.string.credential_push_key_failed,
                                        "Command exited with status " + exitCode),
                                Toast.LENGTH_LONG).show();
                        exportButton.setEnabled(true);
                        exportButton.setText(R.string.credential_export);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Push via active session failed: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            getString(R.string.credential_push_key_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    exportButton.setEnabled(true);
                    exportButton.setText(R.string.credential_export);
                });
            }
        }, "ExportKey-ActiveSession").start();
    }

    /**
     * Create a temporary BLE-Eth transport, push the key through it, then tear down.
     * This is used when there's no active SSH session but BLE is connected to the device.
     */
    private void executeViaBleEthTransport(BluetoothService bluetoothService,
                                            String host, int port,
                                            String username, String password,
                                            String command) {
        new Thread(() -> {
            BleEthTransport transport = null;
            BluetoothService.BleEthDataCallback callback = null;
            try {
                runOnUiThread(() -> exportButton.setText("Setting up BLE tunnel…"));

                // 1. Create temporary BleEthTransport
                transport = new BleEthTransport(bluetoothService::writeBleEthData);

                // 2. Register for incoming BLE-Eth data
                final BleEthTransport transportRef = transport;
                callback = data -> {
                    if (transportRef != null) {
                        transportRef.handleIncomingData(data);
                    }
                };
                bluetoothService.addBleEthCallback(callback);

                // 3. Create SocketFactory (the actual tunnel connect happens inside SshKeyPusher)
                BleEthSocketFactory socketFactory = new BleEthSocketFactory(transport, host, port);

                // 4. Wrap the listener to clean up transport after push completes
                final BleEthTransport transportToClean = transport;
                final BluetoothService.BleEthDataCallback callbackToClean = callback;
                final BluetoothService btService = bluetoothService;
                SshKeyPusher.ProgressListener wrappedListener = new SshKeyPusher.ProgressListener() {
                    @Override
                    public void onProgress(String message) {
                        runOnUiThread(() -> exportButton.setText(message));
                    }

                    @Override
                    public void onSuccess() {
                        cleanupBleEth(btService, callbackToClean, transportToClean);
                        runOnUiThread(() -> {
                            Toast.makeText(ExportPublicKeyActivity.this,
                                    R.string.credential_push_key_success, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        cleanupBleEth(btService, callbackToClean, transportToClean);
                        runOnUiThread(() -> {
                            Toast.makeText(ExportPublicKeyActivity.this,
                                    getString(R.string.credential_push_key_failed, message),
                                    Toast.LENGTH_LONG).show();
                            exportButton.setEnabled(true);
                            exportButton.setText(R.string.credential_export);
                        });
                    }
                };

                // 5. Execute the command (directly, not via pushKey which would echo it)
                SshKeyPusher.executeCommand(host, port, username, password, command, socketFactory,
                        wrappedListener);

            } catch (Exception e) {
                Log.e(TAG, "BLE-Eth push setup failed: " + e.getMessage());
                cleanupBleEth(bluetoothService, callback, transport);
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            getString(R.string.credential_push_key_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    exportButton.setEnabled(true);
                    exportButton.setText(R.string.credential_export);
                });
            }
        }, "ExportKey-BleEth").start();
    }

    /** Clean up temporary BLE-Eth transport and callback. */
    private void cleanupBleEth(BluetoothService bluetoothService,
                                BluetoothService.BleEthDataCallback callback,
                                BleEthTransport transport) {
        try {
            if (callback != null && bluetoothService != null) {
                bluetoothService.removeBleEthCallback(callback);
            }
            if (transport != null) {
                transport.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "BLE-Eth cleanup error: " + e.getMessage());
        }
    }

    private SshKeyPusher.ProgressListener createProgressListener() {
        return new SshKeyPusher.ProgressListener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> exportButton.setText(message));
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ExportPublicKeyActivity.this,
                            R.string.credential_push_key_success, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ExportPublicKeyActivity.this,
                            getString(R.string.credential_push_key_failed, message),
                            Toast.LENGTH_LONG).show();
                    exportButton.setEnabled(true);
                    exportButton.setText(R.string.credential_export);
                });
            }
        };
    }

    /**
     * Strip the comment field from an OpenSSH public key.
     * OpenSSH format: "type base64-key comment"
     * We only want "type base64-key" for authorized_keys.
     */
    private String stripPublicKeyComment(String publicKey) {
        if (publicKey == null || publicKey.isEmpty()) {
            return publicKey;
        }
        // Split by whitespace, take first two parts (type + key)
        String[] parts = publicKey.trim().split("\\s+", 3);
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return publicKey.trim();
    }

    private void setupWindowInsets() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyNonImmersiveSystemBars();
    }

    private void applyNonImmersiveSystemBars() {
        if (Build.VERSION.SDK_INT < 35) {
            int bg = ContextCompat.getColor(this, R.color.background_light);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }

        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decor);
        controller.show(WindowInsetsCompat.Type.systemBars());

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }
}
