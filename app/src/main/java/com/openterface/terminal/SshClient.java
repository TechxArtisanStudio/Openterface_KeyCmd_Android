package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

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
     */
    public void connect() {
        try {
            Log.d(TAG, "SSH connect start: host=" + host + " port=" + port
                    + " viaCustomSocket=" + (socketFactory != null));
            JSch jsch = new JSch();

            // Disable host key checking for local forwarded sessions
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("compression.s2c", "none");
            config.put("compression.c2s", "none");
            // Prefer keyboard-interactive and password auth
            config.put("PreferredAuthentications", "keyboard-interactive,password");
            config.put("PubkeyAuthentication", "no");

            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig(config);

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
            Log.e(TAG, "SSH connect failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            if (listener != null) {
                listener.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
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
            ((ChannelShell) shellChannel).setPtySize(cols, rows, cols * 8, rows * 16);

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

    /** Disconnect SSH session. */
    public void disconnect() {
        Log.d(TAG, "SSH disconnect requested");
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
