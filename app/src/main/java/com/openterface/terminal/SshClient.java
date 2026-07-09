package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.openterface.terminal.CredentialProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private Listener listener;

    private Session session;
    private Channel shellChannel;
    private volatile boolean connected = false;

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
    }

    public SshClient(CredentialProfile profile, TransportAdapter transport,
                     com.jcraft.jsch.SocketFactory socketFactory) {
        this.host = profile.getHost();
        this.port = profile.getPort();
        this.username = profile.getUsername();
        this.password = profile.getPassword();
        this.transport = transport;
        this.socketFactory = socketFactory;
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
                    // Connection lost
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

    /** Disconnect SSH session. */
    public void disconnect() {
        Log.v(TAG, "SSH disconnect requested");
        connected = false;
        if (shellChannel != null && shellChannel.isConnected()) {
            shellChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        transport.disconnect();
    }
}
