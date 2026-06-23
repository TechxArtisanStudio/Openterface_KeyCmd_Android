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
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        BleEthSocket socket = new BleEthSocket(transport);
        socket.connectTunnel(realHost, realPort, 15000);
        return socket;
    }

    @Override
    public InputStream getInputStream(Socket socket) throws IOException {
        return ((BleEthSocket) socket).getInputStream();
    }

    @Override
    public OutputStream getOutputStream(Socket socket) throws IOException {
        return ((BleEthSocket) socket).getOutputStream();
    }
}
