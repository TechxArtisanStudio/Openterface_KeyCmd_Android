package com.openterface.terminal;

import android.util.Log;

import com.openterface.keymod.BuildConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TCP transport over BLE-Eth tunnel.
 *
 * This implements the BLE-to-Ethernet tunneling protocol in Java,
 * mirroring the Python ble_eth_tunnel.py implementation.
 *
 * Protocol:
 *   1. CONNECT -> device opens TCP connection to target
 *   2. DATA (fragmented) -> tunnel TCP payload bidirectionally
 *   3. DISCONNECT -> close tunnel
 *
 * Note: The BLE characteristic writing and notification setup must be
 * wired to the app's BluetoothService or a dedicated BLE connection.
 * Phase 3 provides the protocol implementation; integration with the
 * existing RxAndroidBle connection happens via the WriteCallback and
 * NotificationCallback interfaces.
 */
public class BleEthTransport implements TransportAdapter {

    private static final String TAG = "BleEthTransport";

    // Protocol constants (must match firmware ble_eth_protocol.h)
    // Defined as int because ParsedFrame.cmd is int (unsigned byte).
    private static final int CMD_CONNECT = 0x10;
    private static final int CMD_DATA = 0x11;
    private static final int CMD_DISCONNECT = 0x12;
    private static final int CMD_INFO = 0x1F;
    private static final int CMD_CONNECT_RESP = 0x90;
    private static final int CMD_DATA_RESP = 0x91;
    private static final int CMD_DISCONN_RESP = 0x92;
    private static final int CMD_CONN_CLOSED = 0xD2;
    private static final int CMD_INFO_RESP = 0x9F;

    private static final int MAX_PAYLOAD_LEN = 249;
    private static final int FRAG_HEADER_LEN = 3;
    private static final int MAX_FRAG_DATA = MAX_PAYLOAD_LEN - FRAG_HEADER_LEN; // 246
    private static final byte FRAG_MORE = (byte) 0x80;
    private static final byte FRAG_FIRST = 0x40;

    private Listener listener;
    private int connId = -1;
    private volatile boolean running = false;
    private final Object stateLock = new Object();
    private volatile boolean inputPipeClosed = false;

    // Frame parser and reassembler
    private final FrameParser frameParser = new FrameParser();
    private final DataReassembler dataReassembler = new DataReassembler();

    // Pending connect latch
    private java.util.concurrent.CountDownLatch connectLatch;
    private int pendingConnId = -1;
    private int pendingConnStatus = -1;

    // Queued pipes for JSch integration (replaces fragile Piped* streams)
    private QueuePipe inboundPipe;   // BLE-Eth writes here -> JSch reads from here
    private QueuePipe outboundPipe;  // JSch writes here -> reader thread sends via BLE
    private Thread jschOutputReaderThread;

    /** Callback for writing data to the BLE characteristic. */
    public interface WriteCallback {
        void write(byte[] data);
    }

    private WriteCallback writeCallback;

    public BleEthTransport(WriteCallback writeCallback) {
        this.writeCallback = writeCallback;
    }

    @Override
    public void connect(String host, int port, long timeoutMs) {
        try {
            Log.i(TAG, "BLE-Eth connect start: timeout=" + timeoutMs + "ms");
            synchronized (stateLock) {
                closePipesLocked();
                inputPipeClosed = false;
                running = false;
                connId = -1;
                inboundPipe = new QueuePipe(256);   // BLE-Eth -> JSch
                outboundPipe = new QueuePipe(256);   // JSch -> BLE-Eth
            }
            Log.v(TAG, "BLE-Eth piped streams created");

            // Start thread to read JSch output and send via BLE
            jschOutputReaderThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                InputStream outReader = outboundPipe != null ? outboundPipe.getInputStream() : null;
                if (outReader == null) {
                    Log.e(TAG, "JSch→BLE output reader thread: outbound pipe is null, aborting");
                    return;
                }
                try {
                    Log.v(TAG, "JSch→BLE output reader thread started, waiting for tunnel connection...");
                    while (!Thread.currentThread().isInterrupted()) {
                        // Wait for tunnel to be connected
                        while (!running || connId < 0) {
                            Thread.sleep(50);
                        }
                        Log.v(TAG, "JSch→BLE: tunnel is connected (connId=" + connId + "), reading from pipe...");
                        int len = outReader.read(buffer);
                        if (len > 0) {
                            Log.i(TAG, "JSch→BLE: sending " + len + " bytes");
                            send(buffer, 0, len);
                        } else if (len < 0) {
                            Log.w(TAG, "JSch→BLE: EOF reached, reader exiting");
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "JSch output reader thread ended with IOException: " + e.getMessage());
                    } else {
                        Log.v(TAG, "JSch output reader thread exiting after disconnect: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "JSch output reader thread interrupted");
                }
            }, "BleEth-JSchOutputReader");
            jschOutputReaderThread.start();
            Log.v(TAG, "JSch→BLE output reader thread launched");

            // Build CONNECT frame
            byte[] frame = buildConnect(host, port);
            Log.v(TAG, "BLE-Eth sending CONNECT frame");

            // Set up response listener
            connectLatch = new java.util.concurrent.CountDownLatch(1);

            // Send CONNECT
            if (writeCallback != null) {
                writeCallback.write(frame);
            }

            // Wait for response
            boolean success = connectLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            Log.v(TAG, "BLE-Eth connect latch result: success=" + success
                    + " pendingConnId=" + pendingConnId
                    + " pendingConnStatus=0x" + Integer.toHexString(pendingConnStatus));
            if (!success) {
                if (listener != null) {
                    listener.onError("BLE-Eth CONNECT timed out");
                }
                return;
            }
            if (pendingConnStatus != 0x00) {
                if (listener != null) {
                    listener.onError("BLE-Eth CONNECT failed: status=0x"
                            + Integer.toHexString(pendingConnStatus));
                }
                return;
            }

            connId = pendingConnId;
            running = true;
            Log.v(TAG, "BLE-Eth connect success: connId=" + connId);

        } catch (Exception e) {
            Log.e(TAG, "BLE-Eth connect exception: " + e.getMessage());
            if (listener != null) {
                listener.onError("BLE-Eth connect failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void send(byte[] data, int offset, int len) {
        if (connId < 0 || !running) {
            Log.w(TAG, "send: dropping " + len + " bytes because tunnel is not connected");
            return;
        }

        byte[] payload = new byte[len];
        System.arraycopy(data, offset, payload, 0, len);

        if (len <= MAX_FRAG_DATA) {
            byte[] frame = buildDataSingle(connId, payload);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "send: single frame connId=" + connId
                        + " len=" + len + " frame=" + bytesToHex(frame));
            } else {
                Log.v(TAG, "send: single frame connId=" + connId + " len=" + len);
            }
            if (writeCallback != null) {
                writeCallback.write(frame);
            }
        } else {
            byte[][] frames = buildDataFragmented(connId, payload);
            Log.v(TAG, "send: fragmented into " + frames.length + " frames for " + len + " bytes");
            for (int i = 0; i < frames.length; i++) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "send: fragment[" + i + "/" + frames.length + "]"
                            + " connId=" + connId
                            + " frameLen=" + frames[i].length
                            + " payload=" + bytesToHex(frames[i]));
                } else {
                    Log.v(TAG, "send: fragment[" + i + "/" + frames.length + "]"
                            + " connId=" + connId + " frameLen=" + frames[i].length);
                }
                if (writeCallback != null) {
                    writeCallback.write(frames[i]);
                }
                // Add inter-frame delay (5ms) to safely match Python reference implementation
                // This prevents overwhelming the firmware's BLE receive buffer during fragmentation
                if (i < frames.length - 1) {
                    try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    @Override
    public void disconnect() {
        int disconnectConnId;
        synchronized (stateLock) {
            if (!running && connId < 0 && inputPipeClosed) {
                return;
            }
            running = false;
            disconnectConnId = connId;
            connId = -1;
            inputPipeClosed = true;
            closePipesLocked();
        }
        if (jschOutputReaderThread != null) {
            jschOutputReaderThread.interrupt();
            jschOutputReaderThread = null;
        }
        if (disconnectConnId >= 0) {
            byte[] frame = buildDisconnect(disconnectConnId);
            if (writeCallback != null) {
                writeCallback.write(frame);
            }
        }
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public boolean isConnected() {
        return running && connId >= 0;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Get input stream for JSch to read SSH data from.
     */
    public InputStream getInputStream() {
        return inboundPipe != null ? inboundPipe.getInputStream() : null;
    }

    /**
     * Get output stream for JSch to write SSH data to.
     */
    public OutputStream getOutputStream() {
        return outboundPipe != null ? outboundPipe.getOutputStream() : null;
    }

    /**
     * Call this method with data received from BLE notifications.
     * This feeds the frame parser and dispatches parsed frames.
     */
    public void handleIncomingData(byte[] data) {
        Log.i(TAG, "BLE-Eth RX " + data.length + " bytes");
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "BLE-Eth RX hex: " + bytesToHex(data));
        }
        frameParser.feed(data);
        int frameCount = frameParser.pendingCount();
        if (frameCount > 0) {
            Log.v(TAG, "BLE-Eth parsed " + frameCount + " frame(s), dispatching...");
        } else {
            Log.v(TAG, "BLE-Eth RX did not produce any complete frames (parser buffering)");
        }
        while (!frameParser.isEmpty()) {
            FrameParser.ParsedFrame frame = frameParser.pop();
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "BLE-Eth parsed frame: cmd=0x" + Integer.toHexString(frame.cmd)
                        + " payloadLen=" + frame.payload.length
                        + " payload=" + bytesToHex(frame.payload));
            } else {
                Log.v(TAG, "BLE-Eth parsed frame: cmd=0x" + Integer.toHexString(frame.cmd)
                        + " payloadLen=" + frame.payload.length);
            }
            handleFrame(frame);
        }
    }

    private void handleFrame(FrameParser.ParsedFrame frame) {
        Log.v(TAG, "BLE-Eth handleFrame: cmd=0x" + Integer.toHexString(frame.cmd));
        switch (frame.cmd) {
            case CMD_CONNECT_RESP:
                if (frame.payload.length >= 2) {
                    pendingConnId = frame.payload[0] & 0xFF;
                    pendingConnStatus = frame.payload[1] & 0xFF;
                    Log.i(TAG, "BLE-Eth CONNECT_RESP: connId=" + pendingConnId
                            + " status=0x" + Integer.toHexString(pendingConnStatus));
                    if (connectLatch != null) {
                        Log.v(TAG, "BLE-Eth CONNECT_RESP: counting down latch");
                        connectLatch.countDown();
                    } else {
                        Log.w(TAG, "BLE-Eth CONNECT_RESP: connectLatch is null!");
                    }
                } else {
                    Log.e(TAG, "BLE-Eth CONNECT_RESP: payload too short: " + frame.payload.length + " bytes");
                }
                break;

            case CMD_DATA_RESP: // 0x91 — shared with DATA_PUSH
                Log.v(TAG, "BLE-Eth DATA_RESP/PUSH: payloadLen=" + frame.payload.length);
                if (looksLikeDataAck(frame.payload)) {
                    // ACK from firmware — can be ignored for basic operation
                    Log.v(TAG, "BLE-Eth DATA ACK: status=0x" + Integer.toHexString(frame.payload[0] & 0xFF));
                } else {
                    // Incoming data push
                    Log.i(TAG, "BLE-Eth DATA push: payloadLen=" + frame.payload.length);
                    DataReassembler.ReassembledData reassembled = dataReassembler.feed(frame.payload);
                    if (reassembled != null) {
                        Log.i(TAG, "BLE-Eth reassembled: connId=" + reassembled.connId
                                + " bytes=" + reassembled.data.length);
                        QueuePipe inbound;
                        synchronized (stateLock) {
                            inbound = inboundPipe;
                            if (!running || connId < 0 || inputPipeClosed || inbound == null) {
                                Log.w(TAG, "BLE-Eth: dropping " + reassembled.data.length
                                        + " late bytes because JSch pipe is already closed");
                                return;
                            }
                        }
                        try {
                            OutputStream out = inbound.getOutputStream();
                            if (out != null) {
                                out.write(reassembled.data);
                                Log.v(TAG, "BLE-Eth: wrote " + reassembled.data.length + " bytes to JSch input pipe");
                            }
                        } catch (IOException e) {
                            synchronized (stateLock) {
                                inputPipeClosed = true;
                            }
                            Log.w(TAG, "BLE-Eth: JSch input pipe closed, dropping incoming data: " + e.getMessage());
                        }
                        if (listener != null) {
                            Log.v(TAG, "BLE-Eth: notifying listener of " + reassembled.data.length + " bytes");
                            listener.onDataReceived(reassembled.data, reassembled.data.length);
                        } else {
                            Log.w(TAG, "BLE-Eth: listener is null");
                        }
                    } else {
                        Log.v(TAG, "BLE-Eth: data fragment buffered, waiting for more fragments");
                    }
                }
                break;

            case CMD_DISCONN_RESP:
            case CMD_CONN_CLOSED:
                Log.i(TAG, "BLE-Eth disconnect response: cmd=0x" + Integer.toHexString(frame.cmd));
                if (frame.payload.length >= 1) {
                    int closedConnId = frame.payload[0] & 0xFF;
                    Log.i(TAG, "BLE-Eth disconnect: closedConnId=" + closedConnId + " currentConnId=" + connId);
                    if (closedConnId == connId) {
                        running = false;
                        connId = -1;
                        Log.i(TAG, "BLE-Eth: connection closed, notifying listener");
                        if (listener != null) {
                            listener.onDisconnected();
                        }
                    } else {
                        Log.w(TAG, "BLE-Eth: disconnect for different connId, ignoring");
                    }
                }
                break;

            default:
                Log.v(TAG, "BLE-Eth unknown cmd: 0x" + Integer.toHexString(frame.cmd));
                break;
        }
    }

    private static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    // --- Frame builders ---

    private static byte[] buildFrame(int addr, int cmd, byte[] payload) {
        int payloadLen = payload != null ? payload.length : 0;
        byte[] frame = new byte[6 + payloadLen];
        frame[0] = 0x57;
        frame[1] = (byte) 0xAB;
        frame[2] = (byte) addr;
        frame[3] = (byte) cmd;
        frame[4] = (byte) payloadLen;
        if (payload != null) {
            System.arraycopy(payload, 0, frame, 5, payload.length);
        }
        int checksum = 0;
        for (int i = 0; i < frame.length - 1; i++) {
            checksum += frame[i] & 0xFF;
        }
        frame[frame.length - 1] = (byte) (checksum & 0xFF);
        return frame;
    }

    private static byte[] buildConnect(String ip, int port) {
        String[] parts = ip.split("\\.");
        byte[] payload = new byte[6];
        for (int i = 0; i < 4; i++) {
            payload[i] = (byte) Integer.parseInt(parts[i]);
        }
        payload[4] = (byte) ((port >> 8) & 0xFF);
        payload[5] = (byte) (port & 0xFF);
        return buildFrame(0x00, CMD_CONNECT, payload);
    }

    private static byte[] buildDisconnect(int connId) {
        return buildFrame(0x00, CMD_DISCONNECT, new byte[]{(byte) connId});
    }

    private static byte[] buildDataSingle(int connId, byte[] data) {
        byte[] payload = new byte[3 + data.length];
        payload[0] = FRAG_FIRST;
        payload[1] = 0x00; // seq
        payload[2] = (byte) connId;
        System.arraycopy(data, 0, payload, 3, data.length);
        return buildFrame(0x00, CMD_DATA, payload);
    }

    private static byte[][] buildDataFragmented(int connId, byte[] data) {
        int totalFrags = (data.length + MAX_FRAG_DATA - 1) / MAX_FRAG_DATA;
        byte[][] frames = new byte[totalFrags][];
        int offset = 0;
        for (int seq = 0; seq < totalFrags; seq++) {
            int chunkLen = Math.min(MAX_FRAG_DATA, data.length - offset);
            byte[] payload = new byte[3 + chunkLen];
            byte flags = (byte) (totalFrags & 0x0F);
            if (seq == 0) flags |= FRAG_FIRST;
            if (seq < totalFrags - 1) flags |= FRAG_MORE;
            payload[0] = flags;
            payload[1] = (byte) seq;
            payload[2] = (byte) connId;
            System.arraycopy(data, offset, payload, 3, chunkLen);
            frames[seq] = buildFrame(0x00, CMD_DATA, payload);
            offset += chunkLen;
        }
        return frames;
    }

    private static boolean looksLikeDataAck(byte[] payload) {
        if (payload == null || payload.length == 0) return false;
        int status = payload[0] & 0xFF;
        return status == 0x00 || (status & 0xF0) == 0xE0;
    }

    private void closePipesLocked() {
        if (inboundPipe != null) {
            inboundPipe.close();
            inboundPipe = null;
        }
        if (outboundPipe != null) {
            outboundPipe.close();
            outboundPipe = null;
        }
    }
}
