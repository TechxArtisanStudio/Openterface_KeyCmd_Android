package com.openterface.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import com.openterface.keymod.BuildConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
    private final CredentialProfile profile;
    private volatile Listener listener;
    private Context appContext;

    /** Profile used for (re)connection — stored for auto-reconnect. */
    private CredentialProfile connectProfile;

    private Session session;
    private Channel shellChannel;
    private TerminalSession terminalSession;
    private volatile boolean connected = false;

    /** Lock for synchronizing reconnect operations to prevent race conditions. */
    private final ReentrantLock reconnectLock = new ReentrantLock();

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

    /**
     * Currently running exec thread, or null. Set by {@link #executeCommand}
     * when the background thread starts, cleared when it exits. Allows
     * external callers (e.g. {@code TerminalToolExecutor.cancel()}) to
     * interrupt the real thread running the SSH exec, not a bystander.
     */
    @Nullable private volatile Thread currentExecThread = null;

    /**
     * The currently open {@link ChannelExec}, or null. Used by
     * {@link #cancelCurrentExec()} to close the channel and break the
     * read loop when cancellation is requested.
     */
    @Nullable private volatile ChannelExec currentExecChannel = null;

    public SshClient(String host, int port, String username,
                     String password, TransportAdapter transport) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transport = transport;
        this.socketFactory = null;
        this.profile = null;
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
        this.profile = null;
    }

    public SshClient(CredentialProfile profile, TransportAdapter transport) {
        this.host = profile.getHost();
        this.port = profile.getPort();
        this.username = profile.getUsername();
        this.password = profile.getPassword();
        this.transport = transport;
        this.socketFactory = null;
        this.profile = profile;
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
        this.profile = profile;
        this.connectProfile = profile;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Set application context for host key storage (MITM protection). */
    public void setContext(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
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

    /** Get the profile this client was originally connected with, for profile-equality checks. */
    @Nullable
    public CredentialProfile getConnectProfile() {
        return connectProfile;
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
            // StrictHostKeyChecking="no" skips JSch's built-in host key verification.
            // We cannot use "ask" mode because it triggers a Cipher.isCBC() NPE in the
            // BLE-Eth tunnel scenario (JSch Issue #760 — cipher not initialized when
            // host key callback runs during handshake over custom SocketFactory).
            // Instead, we verify the host key fingerprint at application level AFTER
            // connection succeeds, by comparing against a stored value per host.
            // This provides MITM protection without triggering the JSch bug.
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("compression.s2c", "none");
            config.put("compression.c2s", "none");

            // Configure authentication based on authType
            if (profile.isSshKeyAuth()) {
                // Public key authentication
                String privateKey = profile.getPrivateKey();
                String publicKey = profile.getPublicKey();
                String passphrase = profile.getKeyPassphrase();

                if (privateKey != null && !privateKey.isEmpty()) {
                    // Detect key type for logging
                    String keyType = "unknown";
                    if (privateKey.contains("OPENSSH PRIVATE KEY")) keyType = "OpenSSH";
                    else if (privateKey.contains("RSA PRIVATE KEY")) keyType = "RSA-PEM";
                    else if (privateKey.contains("EC PRIVATE KEY")) keyType = "EC-PEM";
                    Log.v(TAG, "Using SSH key authentication: type=" + keyType
                            + " len=" + privateKey.length()
                            + " hasPassphrase=" + (passphrase != null && !passphrase.isEmpty()));

                    // Load key bytes directly — never write private key to disk
                    byte[] privateKeyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
                    byte[] pubKeyBytes = (publicKey != null && !publicKey.isEmpty())
                            ? publicKey.getBytes(StandardCharsets.UTF_8) : null;
                    byte[] passphraseBytes = (passphrase != null && !passphrase.isEmpty())
                            ? passphrase.getBytes(StandardCharsets.UTF_8) : null;

                    // Use a descriptive identity name for JSch's key management
                    String identityName = "keycmd-" + (profile.getName() != null
                            ? profile.getName().replaceAll("[^a-zA-Z0-9_-]", "_") : "ssh-key");

                    if (pubKeyBytes != null) {
                        jsch.addIdentity(identityName, privateKeyBytes, pubKeyBytes, passphraseBytes);
                        Log.v(TAG, "Loaded key pair (in-memory): type=" + keyType);
                    } else {
                        jsch.addIdentity(identityName, privateKeyBytes, null, passphraseBytes);
                        Log.v(TAG, "Loaded private key only (in-memory): type=" + keyType);
                    }

                    // Diagnostic: log fingerprint only (never log the full key)
                    if (publicKey != null && !publicKey.isEmpty()) {
                        Log.v(TAG, "Public key MD5 fingerprint: " + computePublicKeyFingerprint(publicKey));
                    }

                    config.put("PreferredAuthentications", "publickey");
                    config.put("PubkeyAuthentication", "yes");
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

            // Enable JSch debug logging only in debug builds to avoid leaking
            // internal session/auth details to logcat in production.
            if (BuildConfig.DEBUG) {
                JSch.setLogger(new com.jcraft.jsch.Logger() {
                    @Override
                    public boolean isEnabled(int level) {
                        return level <= com.jcraft.jsch.Logger.DEBUG;
                    }
                    @Override
                    public void log(int level, String message) {
                        Log.d(TAG + "-JSch", message);
                    }
                });
            }

            // UserInfo is required by JSch even with StrictHostKeyChecking="no"
            // (some code paths may still call promptPassword/promptPassphrase).
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

            // Application-level host key verification (MITM protection).
            // Since StrictHostKeyChecking="no" skips JSch's built-in check,
            // we verify the host key fingerprint here against a stored value.
            // Throws on mismatch to prevent silent MITM attacks.
            verifyHostKey(jsch, session);

            connected = true;
            Log.i(TAG, "SSH connect succeeded");

            if (listener != null) {
                listener.onConnected();
            }

            // Fire-and-forget OS probe: populates cachedOs asynchronously so
            // the Agent has the target OS by the time the user submits a prompt
            // (typically 5-10 seconds of human thinking).  Non-blocking.
            probeTargetOsInBackground();

        } catch (HostKeyMismatchException e) {
            // Host key changed — potential MITM. Disconnect and notify user.
            connected = false;
            if (session != null && session.isConnected()) {
                session.disconnect();
                session = null;
            }
            if (transport != null) {
                transport.disconnect();
            }
            Log.e(TAG, "Host key verification failed: " + e.getMessage());
            if (!wasCancelled() && listener != null) {
                listener.onError("HOST_KEY_MISMATCH: " + e.getMessage());
            }

        } catch (Exception e) {
            connected = false;

            // If this thread was interrupted by a newer connection attempt
            // (user switched credentials mid-handshake), the exception is expected —
            // the session/transport were torn down by the new connectBleEth/connectUsbEcm.
            // Skip cleanup and error reporting to avoid spurious error dialogs.
            if (wasCancelled()) {
                Log.v(TAG, "SSH connect cancelled by new connection attempt"
                        + " (exception: " + e.getClass().getSimpleName() + ")");
                // Null out session so future disconnect() calls are no-ops.
                session = null;
                return;
            }

            // Guard against NullPointerException from JSch internals (e.g. Cipher.isCBC())
            // when another thread disconnects our session mid-handshake. This is a known
            // JSch bug (Issue #760) triggered by concurrent disconnect during handshake.
            if (e instanceof NullPointerException) {
                Log.v(TAG, "SSH connect got NPE during handshake (likely concurrent disconnect)"
                        + " — treating as silent cancellation");
                session = null;
                return;
            }

            // Clean up session and transport on failure to prevent stale state
            // from affecting subsequent connection attempts.
            if (session != null) {
                session.disconnect();
                session = null;
            }
            if (transport != null) {
                transport.disconnect();
            }
            // Log the FULL exception with stack trace for debugging
            Log.e(TAG, "SSH connect failed: " + e.getClass().getName()
                    + ": " + e.getMessage(), e);
            String errorMessage = buildErrorMessage(e);
            if (listener != null) {
                listener.onError(errorMessage);
            }
        }
    }

    /**
     * Returns true if this connection attempt was cancelled by a newer connection
     * (the connection thread was interrupted). Used to suppress spurious error
     * dialogs when the user rapidly switches between credentials.
     */
    private boolean wasCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    /**
     * Build an error message that is useful for the UI without leaking credentials.
     * Strips password values and key material from exception messages.
     */
    private String buildErrorMessage(Exception e) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();

        if (message == null || message.isEmpty()) {
            return className + ": Unknown error";
        }

        // Sanitize: strip anything that looks like a password or key blob
        String sanitized = message;
        // Remove password= or passwd= values (e.g. "password=abc123")
        sanitized = sanitized.replaceAll("(?i)(password|passwd|passphrase)\\s*[=:]\\s*[^\\s,;]+", "$1=***");
        // Remove anything that looks like PEM key content
        sanitized = sanitized.replaceAll("-----[A-Z ]+-----", "[KEY_MATERIAL]");
        // Remove base64 blobs longer than 40 chars (likely key/cert data)
        sanitized = sanitized.replaceAll("[A-Za-z0-9+/=]{40,}", "[BLOB]");

        // Tag auth failures with a marker the UI can detect
        String lower = sanitized.toLowerCase();
        if (lower.contains("auth") && (lower.contains("fail") || lower.contains("cancel")
                || lower.contains("denied") || lower.contains("rejected")
                || lower.contains("no more"))) {
            return "AUTH_FAILED: " + sanitized;
        }

        return className + ": " + sanitized;
    }

    /**
     * Compute MD5 fingerprint of an OpenSSH-format public key string.
     * Format: "ssh-ed25519 AAAAC3... comment"
     * Returns the fingerprint as "MD5:aa:bb:cc:..." or null on error.
     */
    private static String computePublicKeyFingerprint(String publicKey) {
        if (publicKey == null || publicKey.isEmpty()) return null;
        try {
            // Extract base64 part (between first space and second space/end)
            String[] parts = publicKey.trim().split("\\s+");
            if (parts.length < 2) return null;
            byte[] keyBytes = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(keyBytes);
            StringBuilder sb = new StringBuilder("MD5:");
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02x", digest[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Application-level host key verification for MITM protection.
     * Since StrictHostKeyChecking="no" skips JSch's built-in check (due to Cipher.isCBC()
     * NPE in BLE-Eth tunnel), we verify the host key fingerprint manually:
     * - First connection: store the fingerprint in encrypted SharedPreferences
     * - Subsequent connections: compare against stored fingerprint
     * - Mismatch: throw HostKeyMismatchException to disconnect and alert the user
     */
    private void verifyHostKey(JSch jsch, Session session) throws HostKeyMismatchException {
        if (appContext == null) {
            Log.w(TAG, "No app context set — skipping host key verification");
            return;
        }

        try {
            HostKey hostKey = session.getHostKey();
            if (hostKey == null) {
                Log.w(TAG, "No host key available from session");
                return;
            }

            String hostPort = host + ":" + port;

            // Compute fingerprint of the host key using JSch's built-in method
            String fingerprint = hostKey.getFingerPrint(jsch);
            Log.v(TAG, "Host key fingerprint for " + hostPort + ": " + fingerprint);

            String storedFingerprint = getStoredFingerprint(hostPort);

            if (storedFingerprint == null) {
                // First connection — store the fingerprint for future verification
                storeFingerprint(hostPort, fingerprint);
                Log.i(TAG, "Host key stored for " + hostPort + ": " + fingerprint);
            } else if (storedFingerprint.equals(fingerprint)) {
                Log.v(TAG, "Host key verified for " + hostPort);
            } else {
                // Host key mismatch — potential MITM attack!
                // Disconnect immediately to protect the user.
                String errorMsg = "Host key changed for " + hostPort
                        + ". Expected: " + storedFingerprint
                        + ", Got: " + fingerprint
                        + ". This may indicate a man-in-the-middle attack"
                        + " or the server key was legitimately regenerated.";
                Log.e(TAG, "HOST KEY MISMATCH: " + errorMsg);
                throw new HostKeyMismatchException(errorMsg);
            }
        } catch (HostKeyMismatchException e) {
            // Re-throw without wrapping — caller handles disconnect.
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "Host key verification failed: " + e.getMessage());
            // Verification error (not mismatch) — allow connection but log warning.
            // This covers edge cases like corrupted storage, missing JSch methods, etc.
        }
    }

    /**
     * Exception thrown when the SSH host key fingerprint doesn't match the stored value.
     * The caller should disconnect and alert the user.
     */
    static class HostKeyMismatchException extends Exception {
        HostKeyMismatchException(String message) {
            super(message);
        }
    }

    private String getStoredFingerprint(String hostPort) {
        try {
            SharedPreferences prefs = getHostKeyPrefs();
            return prefs.getString(hostPort, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read stored fingerprint: " + e.getMessage());
            return null;
        }
    }

    private void storeFingerprint(String hostPort, String fingerprint) {
        try {
            SharedPreferences prefs = getHostKeyPrefs();
            prefs.edit().putString(hostPort, fingerprint).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to store host key fingerprint: " + e.getMessage());
        }
    }

    /**
     * Get encrypted SharedPreferences for host key fingerprint storage.
     * Uses EncryptedSharedPreferences to prevent tampering by attackers with device access.
     */
    private SharedPreferences getHostKeyPrefs() throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                appContext,
                "ssh_host_keys_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /**
     * Legacy method for backward compatibility.
     * Uses the profile passed to the constructor if available (preserving auth type and SSH key),
     * otherwise falls back to a default password-auth profile.
     */
    public void connect() {
        if (profile != null) {
            connect(profile);
        } else {
            // Validate required fields before creating profile
            if (host == null || host.isEmpty()) {
                if (listener != null) {
                    listener.onError("SSH connect failed: host is not set");
                }
                return;
            }
            if (username == null || username.isEmpty()) {
                if (listener != null) {
                    listener.onError("SSH connect failed: username is not set");
                }
                return;
            }
            // Create a default profile with password auth from stored host/user/password
            CredentialProfile defaultProfile = new CredentialProfile();
            defaultProfile.setAuthType(CredentialProfile.AUTH_TYPE_PASSWORD);
            defaultProfile.setHost(host);
            defaultProfile.setPort(port);
            defaultProfile.setUsername(username);
            defaultProfile.setPassword(password);
            connect(defaultProfile);
        }
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
            shellChannel.connect(15000); // 15s timeout for shell channel open

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
     * UserInfo implementation that automatically accepts prompts.
     * Required by JSch even with StrictHostKeyChecking="no" for some code paths
     * that may call promptPassword/promptPassphrase during authentication.
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
            // Auto-accept prompts (host key verification is done at application level)
            Log.d(TAG, "Auto-accepting prompt: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            Log.d(TAG, "JSch message: " + message);
        }
    }

    /**
     * Execute a command on the existing SSH session via ChannelExec.
     * Used by ExportPublicKeyActivity to push keys through the active tunnel
     * (avoids opening a second SSH session on a single-connection transport like BLE-Eth).
     *
     * Wraps the command in bash -c to ensure multi-line snippets (if/then/fi, etc.)
     * are executed as a shell script rather than treated as raw text.
     *
     * @param command Shell command to execute
     * @return exit status of the command, or -1 if execution failed
     * @throws Exception if the channel cannot be opened or the session is not connected
     */
    public int executeCommand(String command) throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("SSH session is not connected");
        }

        // Escape single quotes in the command for safe embedding in bash -c '...'
        String escapedCommand = command.replace("'", "'\\''");

        // Wrap in bash -c to ensure multi-line snippets are interpreted as shell script
        String bashCommand = "bash -c '" + escapedCommand + "'";

        com.jcraft.jsch.ChannelExec channel =
                (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
        channel.setCommand(bashCommand);
        channel.connect(15000);

        // Wait for command to complete
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();
        return exitStatus;
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
        Thread execThread = new Thread(new Runnable() {
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
                    currentExecChannel = execChannel;
                } catch (Exception firstErr) {
                    Log.w(TAG, "Exec attempt failed: " + firstErr.getMessage()
                            + ", attempting reconnect...");
                    // Try reconnect once
                    if (connectProfile != null && forceReconnect()) {
                        Log.i(TAG, "Reconnect succeeded, retrying command");
                        try {
                            execChannel = openExecChannel(command);
                            currentExecChannel = execChannel;
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

                        // Cancellation check — cancelCurrentExec() sets the flag and
                        // closes the channel, but checking here lets us exit promptly
                        // with a clear message instead of relying on the channel close.
                        if (Thread.currentThread().isInterrupted()) {
                            Log.i(TAG, "Exec interrupted — cancelling command");
                            callback.onError("Command cancelled");
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
                    currentExecChannel = null;
                    currentExecThread = null;
                    if (execChannel != null) {
                        execChannel.disconnect();
                    }
                }
            }
        }, "SshExec-" + Math.abs(command.hashCode()));
        currentExecThread = execThread;
        execThread.start();
    }

    /**
     * Cancel any currently running exec command. Interrupts the exec thread
     * and closes the channel, which breaks the read loop in
     * {@link #executeCommand(String, int, ExecCallback)} and triggers an
     * {@code onError("Command cancelled")} callback.
     *
     * <p>Safe to call when no exec is running — does nothing in that case.</p>
     */
    public void cancelCurrentExec() {
        Thread t = currentExecThread;
        if (t != null) {
            t.interrupt();
        }
        ChannelExec ch = currentExecChannel;
        if (ch != null) {
            try { ch.disconnect(); } catch (Exception ignored) {}
        }
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
     * <p>Thread-safe: Uses a ReentrantLock to prevent race conditions when multiple
     * threads attempt to reconnect simultaneously (e.g., parallel exec commands
     * detecting session failure). If another thread is already reconnecting, this
     * method waits up to 30 seconds for it to complete.</p>
     *
     * @return true if reconnection succeeded
     */
    private boolean forceReconnect() {
        CredentialProfile profile = connectProfile;
        if (profile == null) {
            Log.e(TAG, "forceReconnect: no profile stored");
            return false;
        }

        // Try to acquire the reconnect lock with timeout to prevent deadlock
        boolean lockAcquired = false;
        try {
            lockAcquired = reconnectLock.tryLock(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "forceReconnect: interrupted while waiting for lock");
            return false;
        }

        if (!lockAcquired) {
            Log.w(TAG, "forceReconnect: could not acquire lock (another reconnect in progress?)");
            return false;
        }

        try {
            // Double-check: another thread may have reconnected while we waited
            if (isSessionReliable()) {
                Log.i(TAG, "forceReconnect: session already reliable (reconnected by another thread)");
                return true;
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
        } finally {
            reconnectLock.unlock();
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
        shellChannel = null;

        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        session = null;

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
