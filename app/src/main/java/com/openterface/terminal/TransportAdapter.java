package com.openterface.terminal;

/**
 * Abstract transport for raw TCP byte streaming.
 * The SSH client reads/writes through this interface, unaware of the
 * underlying transport (USB or BLE).
 */
public interface TransportAdapter {

    interface Listener {
        void onDataReceived(byte[] data, int len);
        void onDisconnected();
        void onError(String message);
    }

    /**
     * Open a TCP connection to the target.
     * @param host target IP
     * @param port target port
     * @param timeoutMs connection timeout
     */
    void connect(String host, int port, long timeoutMs);

    /** Send data to the remote side. */
    void send(byte[] data, int offset, int len);

    /** Close the transport. */
    void disconnect();

    /** Whether the transport is active. */
    boolean isConnected();

    void setListener(Listener listener);
}
