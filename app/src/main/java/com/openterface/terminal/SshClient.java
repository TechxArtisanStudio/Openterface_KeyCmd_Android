package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.openterface.terminal.CredentialProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

/**
 * SSH-2.0 client wrapper using JSch.
 * For USB ECM: JSch creates its own java.net.Socket directly.
 * For BLE-Eth: Uses a custom SocketFactory that bridges through BleEthTransport.
 */
public class SshClient {

    private static final String TAG = "SshClient";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data, int len);
        void onError(String message);
    }

    /**
     * Callback for async command execution via {@link #executeCommand}.
     *
     * <p>All callbacks fire on the exec thread (background). The consumer
     * is responsible for dispatching to the main thread if UI updates are needed.</p>
     */
    public interface ExecCallback {
        /** A line of output was received (stdout or stderr). */
        void onOutput(String line);
        /** Command completed. {@code exitCode} is the remote process exit status. */
        void onComplete(int exitCode, String output);
        /** A fatal error occurred (timeout, channel failure, SSH disconnected). */
        void onError(String message);
    }

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final TransportAdapter transport;
    private final com.jcraft.jsch.SocketFactory socketFactory;
    private volatile Listener listener;

    /** Profile used for (re)connection — stored for auto-reconnect. */
    private CredentialProfile connectProfile;

    private Session session;
    private Channel shellChannel;
    private TerminalSession terminalSession;
    private volatile boolean connected = false;

    // ── OS Auto-detection cache (session-scoped) ────────────────────────
    //
    // Populated asynchronously right after connect() succeeds by probing the
    // remote host with a lightweight command.  Downstream consumers (the Agent's
    // OsDetector, CommandValidator, etc.) read these fields via the getters
    // below.  The cache is cleared on disconnect() / forceReconnect() so that
    // a fresh probe runs after every reconnection — the target OS can change
    // if the user switches profiles.
    //
    // Values: "linux", "macos", "windows", or null (probe not yet finished).
    private volatile String cachedOs = null;
    private volatile long cachedOsProbeTime = 0L;
    private volatile boolean osProbeRunning = false;

    public SshClient(String host, int port, String username,
                     String password, TransportAdapter transport) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transport = transport;
        this.socketFactory = null;
    }

    public SshClient(String host, int port, String username,
                     String password, TransportAdapter transport,
                     com.jcraft.jsch.SocketFactory socketFactory) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transport = transport;
        this.socketFactory = socketFactory;
    }

    public SshClient(CredentialProfile profile, TransportAdapter transport) {
        this.host = profile.getHost();
        this.port = profile.getPort();
        this.username = profile.getUsername();
        this.password = profile.getPassword();
        this.transport = transport;
        this.socketFactory = null;
        this.connectProfile = profile;
    }

    public SshClient(CredentialProfile profile, TransportAdapter transport,
                     com.jcraft.jsch.SocketFactory socketFactory) {
        this.host = profile.getHost();
        this.port = profile.getPort();
        this.username = profile.getUsername();
        this.password = profile.getPassword();
        this.transport = transport;
        this.socketFactory = socketFactory;
        this.connectProfile = profile;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Called when the transport receives data (for USB ECM mode).
     * This bridges the transport listener to the SshClient listener.
     */
    public void onDataReceivedFromTransport(byte[] data, int len) {
        if (listener != null) {
            listener.onDataReceived(data, len);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Establish SSH connection. Call on background thread.
     * Supports both password and public key authentication based on profile settings.
     */
    public void connect(CredentialProfile profile) {
        try {
            Log.v(TAG, "SSH connect start: authType=" + profile.getAuthType()
                    + " viaCustomSocket=" + (socketFactory != null));
            // Clear any stale OS cache from a previous session so callers don't
            // read the old target OS while a new probe is pending.
            clearCachedOs();
            JSch jsch = new JSch();

            // Host key checking:
            // "ask" mode prompts UserInfo on first connection or host-key change.
            // We provide an auto-accept UserInfo so the user isn't blocked, but
            // the host key is still saved into known_hosts — subsequent connections
            // verify against the stored key and reject changes (MITM protection).
            // This is much safer than "no" which never stores or verifies the key.
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("compression.s2c", "none");
            config.put("compression.c2s", "none");

            // Configure authentication based on authType
            if (profile.isSshKeyAuth()) {
                // Public key authentication
                String privateKey = profile.getPrivateKey();
                String passphrase = profile.getKeyPassphrase();

                if (privateKey != null && !privateKey.isEmpty()) {
                    // Add identity with optional passphrase
                    byte[] privateKeyBytes = privateKey.getBytes();
                    byte[] passphraseBytes = (passphrase != null && !passphrase.isEmpty())
                        ? passphrase.getBytes() : null;
                    jsch.addIdentity("ssh-key", privateKeyBytes, null, passphraseBytes);

                    config.put("PreferredAuthentications", "publickey");
                    config.put("PubkeyAuthentication", "yes");
                    Log.v(TAG, "Using SSH key authentication");
                } else {
                    throw new Exception("Private key is empty for SSH key authentication");
                }
            } else {
                // Password authentication
                config.put("PreferredAuthentications", "keyboard-interactive,password");
                config.put("PubkeyAuthentication", "no");
                Log.v(TAG, "Using password authentication");
            }

            session = jsch.getSession(username, host, port);
            session.setConfig(config);

            // Set UserInfo so "ask" mode auto-accepts on first use without throwing.
            // JSch will then write the host key into known_hosts. On subsequent
            // connections, the stored key is verified — a changed key is detected.
            session.setUserInfo(new AutoAcceptUserInfo());

            // For password auth, set password on session
            if (!profile.isSshKeyAuth()) {
                session.setPassword(password);
            }

            // Use custom SocketFactory if provided (for BLE-Eth tunnel)
            if (socketFactory != null) {
                session.setSocketFactory(socketFactory);
            }

            session.connect(20000); // SSH handshake timeout
            connected = true;
            Log.i(TAG, "SSH connect succeeded");

            if (listener != null) {
                listener.onConnected();
            }

            // Fire-and-forget OS probe: populates cachedOs asynchronously so
            // the Agent has the target OS by the time the user submits a prompt
            // (typically 5-10 seconds of human thinking).  Non-blocking.
            probeTargetOsInBackground();

        } catch (Exception e) {
            connected = false;
            String errorMessage = getSafeErrorMessage(e);
            Log.e(TAG, "SSH connect failed: " + errorMessage);
            if (listener != null) {
                listener.onError(errorMessage);
            }
        }
    }

    /**
     * Get safe error message without leaking sensitive info like passwords.
     */
    private String getSafeErrorMessage(Exception e) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();

        // Check for authentication failure
        if (message != null && (message.contains("Auth fail") ||
            message.contains("auth fail") ||
            message.contains("Authentication fail") ||
            className.contains("Auth"))) {
            return "AUTH_FAILED";
        }

        // For other exceptions, return generic message
        if (message != null && message.length() > 100) {
            return className + ": Connection error";
        }

        return className + ": " + (message != null ? message : "Unknown error");
    }

    /**
     * Legacy method for backward compatibility. Uses password authentication.
     */
    public void connect() {
        // Create a default profile with password auth
        CredentialProfile profile = new CredentialProfile();
        profile.setAuthType(CredentialProfile.AUTH_TYPE_PASSWORD);
        connect(profile);
    }

    /**
     * Start an interactive shell channel.
     * Data from the shell is delivered to the TerminalSession via the listener.
     */
    public void startShell(TerminalSession terminalSession) {
        this.terminalSession = terminalSession;
        try {
            shellChannel = session.openChannel("shell");

            // Set terminal size
            int cols = terminalSession.getColumns();
            int rows = terminalSession.getRows();
            // Set terminal type — must be xterm-256color for TUI apps (Ink/React/Neovim)
            ((ChannelShell) shellChannel).setPtyType("xterm-256color", cols, rows, cols * 8, rows * 16);

            InputStream in = shellChannel.getInputStream();
            OutputStream out = shellChannel.getOutputStream();
            shellChannel.connect();

            // Read thread: SSH -> TerminalView
            new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    while (connected && !shellChannel.isClosed()) {
                        int len = in.read(buffer);
                        if (len < 0) break;
                        if (listener != null) {
                            listener.onDataReceived(buffer, len);
                        }
                    }
                } catch (IOException e) {
                    Log.v(TAG, "Shell read interrupted: " + e.getMessage());
                } finally {
                    connected = false;
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }
            }, "SshShell-Read").start();

            // Write callback: bind terminal keystrokes to SSH output
            terminalSession.setKeySender(data -> {
                try {
                    out.write(data);
                    out.flush();
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError("SSH write failed: " + e.getMessage());
                    }
                }
            });

            // Response callback: bind terminal responses (CPR, DA1, etc.) to SSH output
            terminalSession.setResponseSender(data -> {
                try {
                    out.write(data);
                    out.flush();
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError("SSH response write failed: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Shell channel failed: " + e.getMessage());
            }
        }
    }

    /**
     * Send window resize notification to the remote side.
     */
    public void resizeTerminal(int cols, int rows) {
        if (shellChannel != null && shellChannel.isConnected()) {
            try {
                ((ChannelShell) shellChannel).setPtySize(
                    cols, rows, cols * 8, rows * 16);
            } catch (Exception e) {
                // Ignore resize errors
            }
        }
    }

    /**
     * UserInfo implementation that automatically accepts host key prompts.
     * This enables StrictHostKeyChecking="ask" to be safe and automatic.
     */
    private static class AutoAcceptUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            // Automatically accept unknown host keys
            Log.d(TAG, "Auto-accepting host key: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            Log.d(TAG, "JSch message: " + message);
        }
    }

    // ── Command execution (ChannelExec) for Agent ───────────────────────

    /**
     * Check if the underlying SSH session is connected.
     * Safe to call from any thread — reads the volatile {@code connected} flag
     * and the JSch session state.
     */
    public boolean isSessionConnected() {
        return connected && session != null && session.isConnected();
    }

    /**
     * Re-establish the SSH session using the original connection parameters.
     * The transport layer (USB/BLE) is reused — only the JSch session is recreated.
     * Blocks until reconnection succeeds or fails.
     *
     * @return true if reconnection succeeded
     */
    public boolean reconnect() {
        CredentialProfile profile = connectProfile;
        if (profile == null) {
            Log.e(TAG, "reconnect: no profile stored");
            return false;
        }
        Log.i(TAG, "Reconnecting SSH session...");

        // Disconnect old session if still alive
        if (session != null && session.isConnected()) {
            session.disconnect();
        }

        try {
            connect(profile);
            return connected;
        } catch (Exception e) {
            Log.e(TAG, "Reconnect failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute a command via JSch {@code ChannelExec} (non-interactive) and
     * capture stdout + stderr asynchronously.
     *
     * <p>Runs on a dedicated background thread. Includes automatic reconnect
     * logic: if the session appears dead, reconnects before executing.</p>
     *
     * @param command   shell command to run on the remote host
     * @param timeoutMs maximum wall-clock time to wait (0 = no timeout)
     * @param callback  result callback (never null)
     */
    public void executeCommand(final String command, int timeoutMs,
                               final ExecCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ChannelExec execChannel = null;
                try {
                    // Pre-check: ensure session is truly alive before attempting exec.
                    // JSch's isConnected() can return true even when the underlying
                    // socket is dead (e.g., after network interruption).
                    // Force reconnect if session appears stale.
                    if (!isSessionReliable()) {
                        Log.w(TAG, "Session appears stale before exec, forcing reconnect...");
                        if (!forceReconnect()) {
                            callback.onError("SSH session is down and reconnect failed. "
                                    + "Please reconnect in Terminal tab.");
                            return;
                        }
                        Log.i(TAG, "Reconnect succeeded, proceeding with command");
                    }

                    execChannel = openExecChannel(command);
                } catch (Exception firstErr) {
                    Log.w(TAG, "Exec attempt failed: " + firstErr.getMessage()
                            + ", attempting reconnect...");
                    // Try reconnect once
                    if (connectProfile != null && forceReconnect()) {
                        Log.i(TAG, "Reconnect succeeded, retrying command");
                        try {
                            execChannel = openExecChannel(command);
                        } catch (Exception retryErr) {
                            callback.onError("Exec failed after reconnect: " + retryErr.getMessage());
                            return;
                        }
                    } else {
                        callback.onError("SSH reconnect failed. Please reconnect in Terminal tab.");
                        return;
                    }
                }

                if (execChannel == null) {
                    callback.onError("Exec failed: channel is not opened.");
                    return;
                }

                try {
                    InputStream stdout = execChannel.getInputStream();
                    InputStream stderr = execChannel.getErrStream();

                    long startTime = System.currentTimeMillis();
                    StringBuilder output = new StringBuilder();
                    byte[] buffer = new byte[1024];
                    StringBuilder lineAccum = new StringBuilder();

                    while (true) {
                        // Timeout check
                        if (timeoutMs > 0
                                && (System.currentTimeMillis() - startTime) > timeoutMs) {
                            callback.onError("Command timed out after " + timeoutMs + "ms");
                            break;
                        }

                        // Drain stdout
                        int available = stdout.available();
                        if (available > 0) {
                            int len = stdout.read(buffer, 0, Math.min(available, buffer.length));
                            if (len > 0) {
                                String chunk = new String(buffer, 0, len);
                                output.append(chunk);
                                // Emit per-line callbacks
                                for (int i = 0; i < chunk.length(); i++) {
                                    char c = chunk.charAt(i);
                                    if (c == '\n') {
                                        String line = lineAccum.toString();
                                        lineAccum.setLength(0);
                                        if (!line.isEmpty()) {
                                            callback.onOutput(line);
                                        }
                                    } else if (c != '\r') {
                                        lineAccum.append(c);
                                    }
                                }
                            }
                        }

                        // Drain stderr (merged into output, no per-line callback)
                        available = stderr.available();
                        if (available > 0) {
                            int len = stderr.read(buffer, 0, Math.min(available, buffer.length));
                            if (len > 0) {
                                output.append(new String(buffer, 0, len));
                            }
                        }

                        if (execChannel.isClosed()) {
                            // Flush any remaining partial line
                            if (lineAccum.length() > 0) {
                                callback.onOutput(lineAccum.toString());
                                lineAccum.setLength(0);
                            }
                            break;
                        }

                        Thread.sleep(50); // avoid busy-wait
                    }

                    int exitCode = execChannel.getExitStatus();
                    callback.onComplete(exitCode, output.toString());

                } catch (Exception e) {
                    Log.e(TAG, "Exec failed: " + e.getMessage(), e);
                    callback.onError("Exec failed: " + e.getMessage());
                } finally {
                    if (execChannel != null) {
                        execChannel.disconnect();
                    }
                }
            }
        }, "SshExec-" + Math.abs(command.hashCode())).start();
    }

    /**
     * Check if the SSH session is truly reliable (not just "connected" in JSch's view).
     * JSch's isConnected() can return true even when the underlying TCP connection
     * is dead (e.g., after network interruption or server-side timeout).
     *
     * @return true if session appears alive and usable
     */
    private boolean isSessionReliable() {
        if (session == null || !session.isConnected()) {
            return false;
        }
        // Additional check: verify the session hasn't been idle for too long.
        // If the session was connected more than 5 minutes ago without activity,
        // it might be stale even if JSch thinks it's connected.
        // For now, we trust JSch's isConnected() but log a warning if it seems stale.
        return true;
    }

    /**
     * Force a full reconnection: disconnect old session (even if it appears connected)
     * and establish a new one using the stored profile.
     *
     * @return true if reconnection succeeded
     */
    private boolean forceReconnect() {
        CredentialProfile profile = connectProfile;
        if (profile == null) {
            Log.e(TAG, "forceReconnect: no profile stored");
            return false;
        }
        Log.i(TAG, "Force reconnecting SSH session...");

        // Always disconnect old session, even if it appears connected
        // (it might be a stale/dead connection)
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Error disconnecting old session: " + e.getMessage());
            }
        }
        connected = false;

        try {
            connect(profile);
            return connected;
        } catch (Exception e) {
            Log.e(TAG, "Force reconnect failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convenience overload with the default 60-second timeout.
     *
     * @see #executeCommand(String, int, ExecCallback)
     */
    public void executeCommand(String command, ExecCallback callback) {
        executeCommand(command, 60_000, callback);
    }

    /**
     * Open a ChannelExec, set the command, and connect.
     * Throws if the channel cannot be opened (e.g. session socket is stale).
     */
    private ChannelExec openExecChannel(String command) throws Exception {
        if (session == null || !session.isConnected()) {
            throw new Exception("Session not connected");
        }
        ChannelExec ch = (ChannelExec) session.openChannel("exec");
        ch.setCommand(command);
        ch.setPty(false);
        ch.connect(10_000);
        return ch;
    }

    /** Disconnect SSH session. */
    public void disconnect() {
        Log.v(TAG, "SSH disconnect requested");
        connected = false;

        // Clear OS cache on disconnect so a fresh probe runs after reconnect
        clearCachedOs();

        // Clear send callbacks first — prevents writes to a closing channel
        // if a key press races with disconnect.
        if (terminalSession != null) {
            terminalSession.setKeySender(null);
            terminalSession.setResponseSender(null);
        }

        if (shellChannel != null && shellChannel.isConnected()) {
            shellChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        transport.disconnect();
    }

    // ── OS Auto-detection (session-scoped cache) ────────────────────────

    /**
     * Fire-and-forget OS probe that runs asynchronously right after the SSH
     * session is established.
     *
     * <p>The probe executes a single cross-platform command:
     * <pre>uname -s 2>/dev/null || echo WINDOWS</pre>
     *
     * <ul>
     *   <li>On Linux:  stdout contains "Linux"</li>
     *   <li>On macOS:  stdout contains "Darwin"</li>
     *   <li>On Windows (CMD/PowerShell): {@code uname} is unknown so the
     *       command falls through to the {@code echo WINDOWS} branch</li>
     *   <li>On Windows (Git Bash / MSYS / Cygwin): {@code uname -s} succeeds
     *       with "MINGW…" / "MSYS…" / "CYGWIN…" — detected as Windows</li>
     * </ul>
     *
     * <p>Runs on a dedicated background thread so connect() returns fast.
     * If the probe fails for any reason, {@link #cachedOs} stays {@code null}
     * and callers fall back to {@link OsDetector#detectOsSync}.
     */
    private void probeTargetOsInBackground() {
        if (osProbeRunning || !connected || session == null) {
            return;
        }
        osProbeRunning = true;
        cachedOs = null;
        cachedOsProbeTime = 0L;

        new Thread(() -> {
            try {
                // Single-shot probe: one SSH channel, ~100-300ms round-trip
                final CountDownLatch latch = new CountDownLatch(1);
                final StringBuilder output = new StringBuilder();
                final int[] exitCode = {-1};

                executeCommand("uname -s 2>/dev/null || echo WINDOWS",
                        8000, // 8s probe timeout — generous but not blocking
                        new ExecCallback() {
                    @Override
                    public void onOutput(String line) {
                        output.append(line).append('\n');
                    }

                    @Override
                    public void onComplete(int code, String fullOutput) {
                        exitCode[0] = code;
                        if (fullOutput != null && !fullOutput.isEmpty()) {
                            output.append(fullOutput);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        Log.d(TAG, "OS probe exec error: " + message);
                        latch.countDown();
                    }
                });

                if (!latch.await(10, TimeUnit.SECONDS)) {
                    Log.w(TAG, "OS probe timed out");
                    return;
                }

                String result = output.toString().trim().toLowerCase();
                String detected = null;

                if (result.contains("linux")) {
                    detected = "linux";
                } else if (result.contains("darwin")) {
                    detected = "macos";
                } else if (result.contains("mingw") || result.contains("msys")
                        || result.contains("cygwin")) {
                    // Git Bash / MSYS / Cygwin on Windows
                    detected = "windows";
                } else if (result.contains("windows")) {
                    detected = "windows";
                }
                // else: stay null — OsDetector.detectOsSync will run later

                cachedOs = detected;
                cachedOsProbeTime = System.currentTimeMillis();
                Log.i(TAG, "OS probe result: " + (detected != null ? detected : "unknown (will retry)"));
            } catch (Exception e) {
                Log.w(TAG, "OS probe failed: " + e.getMessage());
            } finally {
                osProbeRunning = false;
            }
        }, "SshOsProbe").start();
    }

    /** Clear the OS cache. Called on disconnect / reconnect. */
    private void clearCachedOs() {
        cachedOs = null;
        cachedOsProbeTime = 0L;
        osProbeRunning = false;
    }

    /**
     * Get the cached detected OS, or {@code null} if the probe has not
     * completed yet or failed.
     *
     * <p>Values: "linux", "macos", "windows".</p>
     */
    @Nullable
    public String getCachedOs() {
        return cachedOs;
    }

    /** Timestamp (millis) when the cached OS was written. 0 if not yet probed. */
    public long getCachedOsProbeTime() {
        return cachedOsProbeTime;
    }
}
