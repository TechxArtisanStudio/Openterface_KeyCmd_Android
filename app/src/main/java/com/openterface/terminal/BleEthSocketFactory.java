package com.openterface.terminal;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * JSch SocketFactory that creates BleEthSocket instances.
 * Bridges JSch's SSH protocol over the BLE-Eth tunnel.
 */
public class BleEthSocketFactory implements com.jcraft.jsch.SocketFactory {
    private static final String TAG = "BleEthSocketFactory";

    private final BleEthTransport transport;
    private final String realHost;
    private final int realPort;

    public BleEthSocketFactory(BleEthTransport transport, String host, int port) {
        this.transport = transport;
        this.realHost = host;
        this.realPort = port;
        Log.d(TAG, "Factory created for " + host + ":" + port);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Log.d(TAG, "createSocket(" + host + ":" + port + ") called");
        BleEthSocket socket = new BleEthSocket(transport);
        Log.d(TAG, "calling connectTunnel");
        socket.connectTunnel(realHost, realPort, 15000);
        Log.d(TAG, "createSocket returning, socket.connected=" + socket.isConnected());
        return socket;
    }

    @Override
    public InputStream getInputStream(Socket socket) throws IOException {
        Log.d(TAG, "getInputStream() called, transport.connected=" + transport.isConnected());
        InputStream is = ((BleEthSocket) socket).getInputStream();
        Log.d(TAG, "getInputStream() returning: " + is);
        return is;
    }

    @Override
    public OutputStream getOutputStream(Socket socket) throws IOException {
        Log.d(TAG, "getOutputStream() called, transport.connected=" + transport.isConnected());
        OutputStream os = ((BleEthSocket) socket).getOutputStream();
        Log.d(TAG, "getOutputStream() returning: " + os);
        return os;
    }
}
