package com.openterface.keymod.agent.executor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;
import com.openterface.terminal.BleEthSocketFactory;
import com.openterface.terminal.BleEthTransport;
import com.openterface.terminal.CredentialProfile;
import com.openterface.terminal.SshClient;
import com.openterface.terminal.TransportAdapter;
import com.openterface.terminal.UsbEcmTransport;

/**
 * Executes terminal steps via SSH ChannelExec.
 *
 * <p>If no SSH session is alive, attempts to auto-connect using the
 * {@link CredentialProfile} stored in {@link MainActivity} (selected via
 * TargetSettingsSheet). Falls back to an error message if no profile
 * is configured.</p>
 */
public final class TerminalToolExecutor implements AgentToolExecutor {

    private static final String TAG = "TerminalToolExecutor";
    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int MAX_OUTPUT_CHARS = 2000;
    private static final int MAX_OUTPUT_LINES = 50;

    private final Context context;
    @Nullable private SshClient sshClient;
    @Nullable private volatile Thread currentThread;
    /** Stores the reason the last auto-connect attempt failed, for surfacing to the user. */
    @Nullable private String lastAutoConnectError;

    public TerminalToolExecutor(@NonNull Context context) {
        // Keep the original context (not getApplicationContext) so that
        // tryAutoConnect() can cast it to MainActivity via instanceof.
        this.context = context;
    }

    /** Set the SSH client to use for command execution. */
    public void setSshClient(@Nullable SshClient client) {
        this.sshClient = client;
    }

    @NonNull
    @Override
    public String getType() {
        return "terminal";
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        if (!"terminal".equals(step.kind)) {
            callback.onFailure("TerminalToolExecutor cannot handle kind=" + step.kind);
            return;
        }

        String command = step.command;
        if (command == null || command.trim().isEmpty()) {
            callback.onFailure("Step has no command");
            return;
        }

        SshClient client = this.sshClient;

        // Auto-connect if no live session but we have a profile
        if (client == null || !client.isSessionConnected()) {
            lastAutoConnectError = null;
            client = tryAutoConnect();
            if (client == null) {
                String reason = lastAutoConnectError != null
                        ? "SSH auto-connect failed: " + lastAutoConnectError
                          + "\n\nOpen Target Settings → check your profile, or connect in Terminal tab first."
                        : "SSH not connected. Open Target Settings → select a profile, or connect in Terminal tab first.";
                callback.onFailure(reason);
                return;
            }
            // Update the stored reference for future calls
            this.sshClient = client;
        }

        Log.i(TAG, "Executing: " + command);
        callback.onProgress(step.index, -1);

        currentThread = Thread.currentThread();

        client.executeCommand(command, DEFAULT_TIMEOUT_MS, new SshClient.ExecCallback() {
            @Override
            public void onOutput(@NonNull String line) {
                Log.v(TAG, "output: " + line);
            }

            @Override
            public void onComplete(int exitCode, @NonNull String output) {
                currentThread = null;
                String truncated = truncateOutput(output);

                if (exitCode == 0) {
                    Log.i(TAG, "Command succeeded: " + command);
                    callback.onSuccess(truncated);
                } else {
                    String errorOutput = "Exit code: " + exitCode;
                    if (!truncated.isEmpty()) {
                        errorOutput += "\n" + truncated;
                    }
                    Log.w(TAG, "Command failed (exit=" + exitCode + "): " + command);
                    callback.onFailure(errorOutput);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                currentThread = null;
                Log.e(TAG, "Exec error: " + message);
                callback.onFailure(message);
            }
        });
    }

    @Override
    public void cancel() {
        Thread t = currentThread;
        if (t != null) {
            t.interrupt();
            currentThread = null;
        }
    }

    /**
     * Try to auto-connect SSH using the profile stored in MainActivity.
     * This lets the Agent work even when the Terminal tab's SSH session died.
     *
     * <p>On failure, stores the reason in {@link #lastAutoConnectError} so
     * the caller can surface a specific error to the user.</p>
     */
    @Nullable
    private SshClient tryAutoConnect() {
        if (!(context instanceof MainActivity)) {
            lastAutoConnectError = "not running in MainActivity";
            Log.w(TAG, "Auto-connect skipped: context is not MainActivity (type="
                    + (context != null ? context.getClass().getSimpleName() : "null") + ")");
            return null;
        }
        MainActivity activity = (MainActivity) context;
        CredentialProfile profile = activity.getActiveSshProfile();
        if (profile == null) {
            lastAutoConnectError = "no SSH profile selected";
            Log.i(TAG, "Auto-connect skipped: no active SSH profile");
            return null;
        }

        Log.i(TAG, "Auto-connecting SSH via profile: " + profile.getDisplayLabel());

        // Determine transport type: BLE if BluetoothService is connected, else USB
        BluetoothService btService = activity.getBluetoothService();
        boolean useBle = btService != null && btService.isConnected();
        Log.i(TAG, "Auto-connect transport: " + (useBle ? "BLE" : "USB"));

        try {
            SshClient client;
            if (useBle) {
                // BLE-Eth transport: requires cleanup + callback registration
                BleEthTransport transport = new BleEthTransport(btService::writeBleEthData);

                // Cleanup stale firmware state: send DISCONNECT frames for all connection IDs
                // Uses the same frame format as TerminalFragment.buildDisconnectFrame()
                Log.i(TAG, "BLE cleanup: sending DISCONNECT frames for connId 0-5");
                for (int cid = 0; cid <= 5; cid++) {
                    btService.writeBleEthData(buildBleDisconnectFrame(cid));
                    Thread.sleep(50);
                }
                // Wait for firmware to process disconnects (TerminalFragment uses 3000ms)
                Log.i(TAG, "BLE cleanup: waiting 3000ms for firmware...");
                Thread.sleep(3000);
                Log.i(TAG, "BLE cleanup: complete");

                // Register callback
                BluetoothService.BleEthDataCallback callback = data -> {
                    if (transport != null) {
                        transport.handleIncomingData(data);
                    }
                };
                btService.addBleEthCallback(callback);

                String host = profile.getHost();
                int port = profile.getPort();
                BleEthSocketFactory socketFactory = new BleEthSocketFactory(transport, host, port);

                client = new SshClient(profile, transport, socketFactory);
            } else {
                client = new SshClient(profile, new UsbEcmTransport());
            }

            // Capture SSH-level errors (auth failure, tunnel failure, etc.)
            final String[] sshError = {null};
            client.setListener(new SshClient.Listener() {
                @Override public void onConnected() {}
                @Override public void onDisconnected() {}
                @Override public void onDataReceived(byte[] data, int len) {}
                @Override public void onError(String message) {
                    sshError[0] = message;
                    Log.w(TAG, "Auto-connect SSH listener error: " + message);
                }
            });
            client.connect(profile);

            if (client.isSessionConnected()) {
                Log.i(TAG, "Auto-connect SSH succeeded via " + (useBle ? "BLE" : "USB"));
                activity.setSshClient(client);
                return client;
            } else {
                // Connect returned without exception but session is not alive
                lastAutoConnectError = sshError[0] != null
                        ? sshError[0]
                        : "SSH session not connected (check host/port/credentials)";
                Log.w(TAG, "Auto-connect SSH failed: " + lastAutoConnectError);
                return null;
            }
        } catch (Exception e) {
            lastAutoConnectError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Log.e(TAG, "Auto-connect SSH exception: " + lastAutoConnectError, e);
            return null;
        }
    }

    /** Build a BLE DISCONNECT frame (matches TerminalFragment.buildDisconnectFrame format). */
    private byte[] buildBleDisconnectFrame(int connId) {
        byte[] frame = new byte[7];
        frame[0] = (byte) 0x57; // header byte 1
        frame[1] = (byte) 0xAB; // header byte 2
        frame[2] = 0x00;        // addr
        frame[3] = 0x12;        // CMD_DISCONNECT
        frame[4] = 0x01;        // payload length
        frame[5] = (byte) connId;
        int checksum = 0;
        for (int i = 0; i < 6; i++) {
            checksum += frame[i] & 0xFF;
        }
        frame[6] = (byte) (checksum & 0xFF);
        return frame;
    }

    /**
     * Truncate output to prevent UI overflow.
     * Max 2000 chars or 50 lines, whichever is hit first.
     */
    @NonNull
    String truncateOutput(@NonNull String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) {
            String[] lines = output.split("\n", -1);
            if (lines.length <= MAX_OUTPUT_LINES) {
                return output;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_OUTPUT_LINES; i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            sb.append("\n... (").append(lines.length - MAX_OUTPUT_LINES)
              .append(" more lines truncated)");
            return sb.toString();
        }
        return output.substring(0, MAX_OUTPUT_CHARS)
                + "\n... (" + (output.length() - MAX_OUTPUT_CHARS) + " chars truncated)";
    }
}
