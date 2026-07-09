package com.openterface.terminal;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP transport over USB CDC ECM.
 *
 * When the device is in HID+ECM Bridge mode (Mode 4), Android creates
 * a virtual network interface (usb0). The target machine is reachable
 * at its IP on this interface (typically 192.168.x.x subnet).
 *
 * This transport uses standard Java Sockets since ECM provides
 * a standard IP network interface.
 */
public class UsbEcmTransport implements TransportAdapter {

    private static final String TAG = "UsbEcmTransport";
    private Listener listener;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running = false;

    @Override
    public void connect(String host, int port, long timeoutMs) {
        Log.v(TAG, "connect() called: timeoutMs=" + timeoutMs);
        try {
            socket = new Socket();
            Log.v(TAG, "Socket created, attempting connect");
            socket.connect(new InetSocketAddress(host, port), (int) timeoutMs);
            Log.v(TAG, "Socket connected successfully");
            socket.setSoTimeout(0); // blocking read
            socket.setTcpNoDelay(true); // SSH needs low latency

            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            running = true;
            Log.v(TAG, "Transport ready, starting read thread");

            // Start read thread
            new Thread(this::readLoop, "UsbEcm-Read").start();

        } catch (IOException e) {
            Log.e(TAG, "connect() failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            if (listener != null) {
                listener.onError("USB ECM connect failed: " + e.getMessage());
            } else {
                Log.e(TAG, "connect() error: listener is null!");
            }
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[4096];
        try {
            while (running) {
                int len = inputStream.read(buffer);
                if (len < 0) break;
                if (listener != null) {
                    listener.onDataReceived(buffer, len);
                }
            }
        } catch (java.io.IOException e) {
            if (running && listener != null) {
                listener.onDisconnected();
            }
        }
    }

    @Override
    public void send(byte[] data, int offset, int len) {
        try {
            outputStream.write(data, offset, len);
            outputStream.flush();
        } catch (java.io.IOException e) {
            if (listener != null) {
                listener.onError("USB ECM send failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void disconnect() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (java.io.IOException e) { /* ignore */ }
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && running;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
