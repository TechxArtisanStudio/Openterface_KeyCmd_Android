package com.openterface.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * In-process TCP proxy for BLE-Eth SSH.
 * Listens on localhost:port, bridges connections through BleEthTransport.
 * JSch connects to localhost:localPort as if it were a real SSH server.
 *
 * This allows the standard JSch SSH client to work over BLE-Eth,
 * which doesn't provide a raw socket interface.
 */
public class BleSshProxy {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private final int localPort;
    private final BleEthTransport transport;
    private volatile boolean running = false;

    private Thread proxyThread;

    public BleSshProxy(int localPort, BleEthTransport transport) {
        this.localPort = localPort;
        this.transport = transport;
    }

    /**
     * Start the proxy server. Call before JSch connects.
     * JSch should then connect to localhost:localPort.
     */
    public void start() {
        proxyThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(localPort, 1, InetAddress.getByName("127.0.0.1"));
                running = true;

                // Accept one client connection
                clientSocket = serverSocket.accept();

                // Bidirectional forwarding: client <-> transport
                forwardBidirectional(clientSocket, transport);
            } catch (IOException e) {
                running = false;
            }
        }, "BleSshProxy");
        proxyThread.start();
    }

    /**
     * Wait for the proxy to be ready (client accepted).
     */
    public void waitForReady(long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!running && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(50);
        }
    }

    /**
     * Start bidirectional forwarding between the TCP socket and BLE transport.
     */
    private void forwardBidirectional(Socket clientSocket, TransportAdapter transport) {
        // Local -> BLE
        Thread localToBle = new Thread(() -> {
            try {
                InputStream in = clientSocket.getInputStream();
                byte[] buf = new byte[4096];
                while (running) {
                    int len = in.read(buf);
                    if (len < 0) break;
                    transport.send(buf, 0, len);
                }
            } catch (IOException e) {
                running = false;
            }
        }, "LocalToBle");
        localToBle.start();

        // BLE -> Local (via transport listener)
        transport.setListener(new TransportAdapter.Listener() {
            @Override
            public void onDataReceived(byte[] data, int len) {
                try {
                    clientSocket.getOutputStream().write(data, 0, len);
                    clientSocket.getOutputStream().flush();
                } catch (IOException e) {
                    running = false;
                }
            }

            @Override
            public void onDisconnected() {
                try { clientSocket.close(); } catch (Exception e) {}
                running = false;
            }

            @Override
            public void onError(String message) {
                try { clientSocket.close(); } catch (Exception e) {}
                running = false;
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {}
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {}
        if (proxyThread != null && proxyThread.isAlive()) {
            proxyThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }
}
