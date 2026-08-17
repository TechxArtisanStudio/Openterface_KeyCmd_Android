package com.openterface.keymod.agent.executor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.agent.core.AgentEnvironment;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;
import com.openterface.keymod.agent.core.CommandValidator;
import com.openterface.keymod.agent.util.PathHelper;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.openterface.terminal.BleEthSocketFactory;
import com.openterface.terminal.BleEthTransport;
import com.openterface.terminal.CredentialProfile;
import com.openterface.terminal.SshClient;
import com.openterface.terminal.UsbEcmTransport;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes terminal steps via SSH ChannelExec.
 *
 * <p>If no SSH session is alive, attempts to auto-connect using the
 * {@link CredentialProfile} stored via {@link AgentEnvironment} (selected via
 * TargetSettingsSheet). If SSH is unavailable and a HID device is connected,
 * falls back to typing the command as HID keystrokes into the active terminal
 * window (output cannot be captured in this mode).</p>
 */
public final class TerminalToolExecutor implements AgentToolExecutor {

    private static final String TAG = "TerminalToolExecutor";
    /** SSH command execution timeout (public so PlanExecutionUseCase can base its latch timeout on it). */
    public static final int DEFAULT_TIMEOUT_MS = 60_000;
    /** Output truncation limits — generous enough for filtered commands, but still a safety net. */
    private static final int MAX_OUTPUT_CHARS = 4000;
    private static final int MAX_OUTPUT_LINES = 100;

    private final Context context;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    @Nullable private AgentEnvironment environment;
    @Nullable private SshClient sshClient;
    @Nullable private ConnectionManager connectionManager;
    @NonNull private String targetOs = "macos"; // TODO(release): revert default to "linux"
    @Nullable private volatile Thread currentThread;
    /** Stores the reason the last auto-connect attempt failed, for surfacing to the user. */
    @Nullable private String lastAutoConnectError;

    public TerminalToolExecutor(@NonNull Context context) {
        this.context = context;
    }

    /** Set the host environment for SSH profile and BLE service access. */
    public void setEnvironment(@NonNull AgentEnvironment environment) {
        this.environment = environment;
    }

    /** Set the SSH client to use for command execution. */
    public void setSshClient(@Nullable SshClient client) {
        this.sshClient = client;
    }

    /** Set the HID ConnectionManager for fallback when SSH is unavailable. */
    public void setConnectionManager(@Nullable ConnectionManager cm) {
        this.connectionManager = cm;
    }

    /** Set the target OS for HID Unicode input method. */
    public void setTargetOs(@NonNull String os) {
        this.targetOs = os;
    }

    @NonNull
    @Override
    public String getType() {
        return "terminal";
    }

    /**
     * Pre-connect SSH so the session is ready before step execution begins.
     * Called during THINKING phase (while LLM generates the plan) to avoid
     * connection latency during EXECUTING. Safe to call multiple times —
     * returns immediately if already connected.
     * Runs the connection attempt on a background thread to avoid blocking UI.
     *
     * @param onConnected callback fired on main thread when connection succeeds
     */
    public void preConnectSsh(@Nullable Runnable onConnected) {
        if (environment == null) return;
        CredentialProfile activeProfile = environment.getActiveSshProfile();
        if (activeProfile != null) {
            SshClient existing = environment.getSshClient();
            if (existing != null && existing.isSessionConnected()) {
                return; // Already connected
            }
            new Thread(() -> {
                lastAutoConnectError = null;
                SshClient client = tryAutoConnectWithProfile(activeProfile);
                if (client != null) {
                    this.sshClient = client;
                    if (onConnected != null) {
                        mainHandler.post(onConnected);
                    }
                }
            }, "AgentPreConnect").start();
        }
    }

    /** Overload for callers that don't need a connection callback. */
    public void preConnectSsh() {
        preConnectSsh(null);
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        if (!"terminal".equals(step.kind)) {
            callback.onFailure("Error: TerminalToolExecutor cannot handle kind=" + step.kind);
            return;
        }

        String command = step.command;
        if (command == null || command.trim().isEmpty()) {
            callback.onFailure("Error: Step has no command");
            return;
        }

        SshClient client = null;

        // Priority 1: Reuse existing SSH session from the environment if still alive.
        // Avoids expensive BLE disconnect/reconnect + SSH handshake on every step.
        if (environment != null) {
            SshClient existing = environment.getSshClient();
            if (existing != null && existing.isSessionConnected()) {
                client = existing;
                Log.d(TAG, "Reusing existing SSH session (no reconnect needed)");
            }
        }

        // Priority 2: Existing session dead or absent — reconnect via profile.
        if (client == null && environment != null) {
            CredentialProfile activeProfile = environment.getActiveSshProfile();

            if (activeProfile != null) {
                lastAutoConnectError = null;
                client = tryAutoConnectWithProfile(activeProfile);
            }
        }

        // Priority 3: Fallback — use the executor's own sshClient reference
        // (e.g. set via setSshClient() or preConnectSsh())
        if (client == null || !client.isSessionConnected()) {
            client = this.sshClient;
            if (client != null && !client.isSessionConnected()) {
                client = null; // Terminal's connection is dead
            }
        }

        // Last resort: try auto-connect with any available profile
        if (client == null) {
            lastAutoConnectError = null;
            client = tryAutoConnect();
        }

        if (client == null || !client.isSessionConnected()) {
            // SSH not available — fall back to HID keystroke input
            if (connectionManager != null && connectionManager.isConnected()) {
                Log.i(TAG, "SSH unavailable, falling back to HID keystroke input for: " + command);
                executeViaHid(command, callback);
                return;
            }

            String reason = lastAutoConnectError != null
                    ? "Error: SSH auto-connect failed: " + lastAutoConnectError
                      + "\n\nOpen Target Settings → check your profile, or connect in Terminal tab first."
                    : "Error: SSH not connected. Open Target Settings → select a profile, or connect in Terminal tab first.";
            callback.onFailure(reason);
            return;
        }

        // ── OS Command Auto-Correction (best effort) ──
        // Run FIRST so that common OS-specific mismatches get fixed before
        // the danger/validation gates run.  If correction is applied, the
        // subsequent checks operate on the corrected command, which is the
        // right thing — danger and validation should assess what will
        // actually execute, not the original wrong-OS command.
        final String originalCommand = command;
        String correctedCommand = CommandValidator.correctCommandForOs(command, targetOs);
        final String effectiveCommand;  // effectively final, used by callback
        if (!correctedCommand.equals(command)) {
            Log.i(TAG, "Auto-corrected command for " + targetOs + ": '"
                    + command + "' → '" + correctedCommand + "'");
            effectiveCommand = correctedCommand;
        } else {
            effectiveCommand = command;
        }

        // ── P0-1: Command Danger Check ──
        // Must run BEFORE SSH connection attempt so that blocked commands
        // are refused even when no SSH session is alive.
        try {
            CommandValidator.DangerResult danger =
                    CommandValidator.checkCommandDanger(effectiveCommand);
            if (danger.level == CommandValidator.DangerLevel.BLOCKED) {
                Log.w(TAG, "Blocked dangerous command: " + effectiveCommand
                        + " — " + danger.reason);
                throw new CommandValidator.DangerousCommandException(danger);
            }
            if (danger.level == CommandValidator.DangerLevel.DANGEROUS) {
                Log.w(TAG, "Dangerous command detected (proceeding with warning): "
                        + effectiveCommand + " — " + danger.reason);
                // TODO(P1): Surface confirmation dialog to user via callback
            }
        } catch (CommandValidator.DangerousCommandException e) {
            callback.onFailure("⛔ Blocked: " + e.getDangerResult().reason);
            return;
        }

        // ─ OS Command Validation (safety net) ──
        // Block execution if the (possibly corrected) command is still wrong
        // for the target OS.  This is the final gate before SSH execution.
        CommandValidator.ValidationResult validation =
                CommandValidator.validate(effectiveCommand, targetOs);
        if (!validation.valid) {
            Log.w(TAG, "Blocked OS-mismatched command for " + targetOs + ": "
                    + effectiveCommand + " — " + validation.message
                    + (originalCommand.equals(effectiveCommand) ? ""
                            : " (original: '" + originalCommand + "')"));
            callback.onFailure("⚠️ OS mismatch: " + validation.message);
            return;
        }

        // ─ PATH Augmentation for non-interactive SSH sessions ─
        // SSH non-interactive shells don't load .zshrc/.bashrc, so common tools
        // (fastfetch, htop, brew, etc.) may not be in PATH.
        // Wrap command with PATH setup if the tool likely needs it.
        String finalCommand = effectiveCommand;
        if (PathHelper.mayNeedPathAugmentation(effectiveCommand, targetOs)) {
            finalCommand = PathHelper.wrapWithPathVariable(effectiveCommand, targetOs);
            Log.d(TAG, "PATH augmented: " + finalCommand);
        }

        Log.i(TAG, "Executing: " + finalCommand);
        callback.onProgress(step.index, -1);

        currentThread = Thread.currentThread();

        client.executeCommand(finalCommand, DEFAULT_TIMEOUT_MS, new SshClient.ExecCallback() {
            @Override
            public void onOutput(@NonNull String line) {
                Log.v(TAG, "output: " + line);
                callback.onOutputLine(step.index, line);
            }

            @Override
            public void onComplete(int exitCode, @NonNull String output) {
                currentThread = null;
                String truncated = truncateOutput(output);

                if (exitCode == 0) {
                    Log.i(TAG, "Command succeeded: " + effectiveCommand);
                    callback.onSuccess(truncated);
                } else {
                    String errorOutput = "Exit code: " + exitCode;
                    if (!truncated.isEmpty()) {
                        errorOutput += "\n" + truncated;
                    }
                    Log.w(TAG, "Command failed (exit=" + exitCode + "): " + effectiveCommand);
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

    /**
     * HID fallback: type the command into the active terminal window via keystrokes.
     * Used when SSH is unavailable (HID mode without SSH profile).
     * Output cannot be captured — command is typed + Enter sent.
     */
    private void executeViaHid(@NonNull String command, @NonNull ExecutionCallback callback) {
        new Thread(() -> {
            currentThread = Thread.currentThread();
            try {
                ConnectionManager cm = connectionManager;
                if (cm == null || !cm.isConnected()) {
                    currentThread = null;
                    callback.onFailure("Error: HID device not connected for terminal fallback");
                    return;
                }

                // Wait for terminal window to be ready after the previous HID step
                // opened it. The HID step's internal <DELAY3S> only delays within
                // its own keystroke stream — the next step fires immediately after.
                Log.i(TAG, "HID fallback: waiting 2s for terminal window to be ready...");
                Thread.sleep(2000);

                // Type the command followed by Enter
                String payload = command + "<ENTER>";
                Log.i(TAG, "HID fallback: typing command via keystrokes: " + command);
                callback.onProgress(-1, -1);

                AtomicBoolean cancelFlag = new AtomicBoolean(false);
                HidTextKeystrokeSender.Result result = HidTextKeystrokeSender.send(
                        payload, cm, targetOs, true, cancelFlag, null);

                currentThread = null;

                if (result == HidTextKeystrokeSender.Result.CANCELLED) {
                    callback.onFailure("Error: HID fallback cancelled");
                } else {
                    Log.i(TAG, "HID fallback: command typed successfully: " + command);
                    callback.onSuccess("✅ Command typed: " + command
                            + "\n(Output not captured in HID mode)");
                }
            } catch (InterruptedException e) {
                currentThread = null;
                Log.i(TAG, "HID fallback interrupted");
                callback.onFailure("Error: HID fallback interrupted");
            } catch (Exception e) {
                currentThread = null;
                Log.e(TAG, "HID fallback failed", e);
                callback.onFailure("Error: HID fallback failed: " + e.getMessage());
            }
        }, "TerminalHidFallback").start();
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
     * Try to auto-connect SSH using a specific profile.
     * This is called when Target Settings has a profile selected — it takes priority
     * over Terminal's existing connection.
     *
     * <p>On failure, stores the reason in {@link #lastAutoConnectError} so
     * the caller can surface a specific error to the user.</p>
     */
    @Nullable
    private SshClient tryAutoConnectWithProfile(@NonNull CredentialProfile profile) {
        if (environment == null) {
            lastAutoConnectError = "no AgentEnvironment set";
            Log.w(TAG, "Auto-connect skipped: environment not set");
            return null;
        }

        Log.i(TAG, "Connecting SSH via Target Settings profile: " + profile.getDisplayLabel());

        // Determine transport type: BLE if BluetoothService is connected, else USB
        BluetoothService btService = environment.getBluetoothService();
        boolean useBle = btService != null && btService.isConnected();
        Log.i(TAG, "Auto-connect transport: " + (useBle ? "BLE" : "USB"));

        try {
            SshClient client;
            if (useBle) {
                // BLE-Eth transport: requires cleanup + callback registration
                BleEthTransport transport = new BleEthTransport(btService::writeBleEthData);

                // Cleanup stale firmware state: send DISCONNECT frames for all connection IDs
                Log.i(TAG, "BLE cleanup: sending DISCONNECT frames for connId 0-5");
                for (int cid = 0; cid <= 5; cid++) {
                    btService.writeBleEthData(buildBleDisconnectFrame(cid));
                    Thread.sleep(50);
                }
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

            // Capture SSH-level errors
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
                Log.i(TAG, "SSH connected via Target Settings profile: " + profile.getDisplayLabel());
                disconnectOldSshClient();
                environment.setSshClient(client);
                return client;
            } else {
                lastAutoConnectError = sshError[0] != null
                        ? sshError[0]
                        : "SSH session not connected (check host/port/credentials)";
                Log.w(TAG, "SSH connect failed: " + lastAutoConnectError);
                return null;
            }
        } catch (Exception e) {
            lastAutoConnectError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Log.e(TAG, "SSH connect exception: " + lastAutoConnectError, e);
            return null;
        }
    }

    /**
     * Try to auto-connect SSH using the profile stored in the environment.
     * This lets the Agent work even when the Terminal tab's SSH session died.
     *
     * <p>On failure, stores the reason in {@link #lastAutoConnectError} so
     * the caller can surface a specific error to the user.</p>
     */
    @Nullable
    private SshClient tryAutoConnect() {
        if (environment == null) {
            lastAutoConnectError = "no AgentEnvironment set";
            Log.w(TAG, "Auto-connect skipped: environment not set");
            return null;
        }
        CredentialProfile profile = environment.getActiveSshProfile();
        if (profile == null) {
            lastAutoConnectError = "no SSH profile selected";
            Log.i(TAG, "Auto-connect skipped: no active SSH profile");
            return null;
        }

        Log.i(TAG, "Auto-connecting SSH via profile: " + profile.getDisplayLabel());

        // Determine transport type: BLE if BluetoothService is connected, else USB
        BluetoothService btService = environment.getBluetoothService();
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
                disconnectOldSshClient();
                environment.setSshClient(client);
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
     * Disconnect the previous SSH client stored in the environment, if any.
     * Prevents resource leaks (JSch session, BLE transport) when reconnecting.
     */
    private void disconnectOldSshClient() {
        if (environment == null) return;
        SshClient old = environment.getSshClient();
        if (old != null && old.isSessionConnected()) {
            Log.d(TAG, "Disconnecting old SSH client before replacing");
            try {
                old.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Error disconnecting old SSH client", e);
            }
        }
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
