package com.openterface.terminal;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * A fake java.net.Socket that bridges JSch I/O to BleEthTransport.
 *
 * JSch expects a java.net.Socket, but we don't have a real TCP connection.
 * BleEthTransport provides InputStream/OutputStream via PipedInputStream/OutputStream
 * that JSch needs, while the actual data goes through BLE-Eth.
 *
 * Architecture:
 *   JSch reads  <- bleEthTransport.getInputStream() <- BLE data parsed by FrameParser
 *   JSch writes -> bleEthTransport.getOutputStream() -> BLE-Eth send()
 */
public class BleEthSocket extends Socket {

    private static final String TAG = "BleEthSocket";

    // Streams exposed to JSch
    private InputStream bleToJsInput;   // JSch reads SSH data from BLE here
    private OutputStream jsToBleOutput;  // JSch writes SSH data to BLE here

    private final BleEthTransport transport;
    private volatile boolean connected = false;
    private volatile int soTimeoutMs = 0;

    public BleEthSocket(BleEthTransport transport) {
        this.transport = transport;
    }

    /**
     * Called by JSch's SocketFactory.getInputStream(socket).
     * Returns the input stream that JSch reads SSH responses from.
     * Overrides Socket.getInputStream() — JSch uses this to read.
     */
    @Override
    public InputStream getInputStream() {
        return bleToJsInput;
    }

    /**
     * Called by JSch's SocketFactory.getOutputStream(socket).
     * Returns the output stream that JSch writes SSH commands to.
     * Overrides Socket.getOutputStream() — JSch uses this to write.
     */
    @Override
    public OutputStream getOutputStream() {
        return jsToBleOutput;
    }

    /** Kept for explicit access — same as getInputStream(). */
    public InputStream getBleToJsInputStream() {
        return bleToJsInput;
    }

    /** Kept for explicit access — same as getOutputStream(). */
    public OutputStream getJsToBleOutputStream() {
        return jsToBleOutput;
    }

    /**
     * Establish the BLE-Eth tunnel.
     * Sets up piped streams and connects the transport.
     * Called by BleEthSocketFactory.createSocket().
     */
    public void connectTunnel(String host, int port, long timeoutMs) throws IOException {
        try {
            // Establish BLE-Eth tunnel (this sets up the piped streams internally)
            Log.v(TAG, "Connecting BLE-Eth tunnel");
            transport.connect(host, port, timeoutMs);

            if (!transport.isConnected()) {
                throw new IOException("BLE-Eth tunnel connection failed");
            }

            // Get the piped streams from transport
            bleToJsInput = transport.getInputStream();
            jsToBleOutput = transport.getOutputStream();

            connected = true;
            Log.v(TAG, "BLE-Eth tunnel connected");

        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("BLE-Eth connect failed: " + e.getMessage());
        }
    }

    @Override
    public void connect(java.net.SocketAddress endpoint, int timeout) throws IOException {
        // This shouldn't be called since we use SocketFactory
        throw new IOException("Direct connect not supported on BLE-Eth socket");
    }

    @Override
    public synchronized void setSoTimeout(int timeout) {
        soTimeoutMs = timeout;
        transport.setReadTimeout(timeout);
    }

    @Override
    public synchronized int getSoTimeout() {
        return soTimeoutMs;
    }

    @Override
    public void setTcpNoDelay(boolean on) throws java.net.SocketException {
        // No-op - BLE-Eth doesn't use TCP nagle algorithm
    }

    @Override
    public synchronized void close() throws IOException {
        connected = false;
        transport.disconnect();
        Log.v(TAG, "BLE-Eth socket closed");
    }

    @Override
    public boolean isConnected() {
        return connected && transport.isConnected();
    }

    @Override
    public boolean isClosed() {
        return !connected;
    }

    @Override
    public boolean isInputShutdown() {
        return !connected;
    }

    @Override
    public boolean isOutputShutdown() {
        return !connected;
    }
}
