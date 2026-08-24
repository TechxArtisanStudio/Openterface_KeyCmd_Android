package com.openterface.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.jcraft.jsch.Channel;
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

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final TransportAdapter transport;
    private final com.jcraft.jsch.SocketFactory socketFactory;
    private final CredentialProfile profile;
    private volatile Listener listener;
    private Context appContext;

    private Session session;
    private Channel shellChannel;
    private TerminalSession terminalSession;
    private volatile boolean connected = false;

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

    /**
     * Establish SSH connection. Call on background thread.
     * Supports both password and public key authentication based on profile settings.
     */
    public void connect(CredentialProfile profile) {
        try {
            Log.v(TAG, "SSH connect start: authType=" + profile.getAuthType()
                    + " viaCustomSocket=" + (socketFactory != null));
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

    /** Disconnect SSH session. */
    public void disconnect() {
        Log.v(TAG, "SSH disconnect requested");
        connected = false;

        // Clear send callbacks first — prevents writes to a closing channel
        // if a key press races with disconnect (Day 5 §3.2).
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
}
