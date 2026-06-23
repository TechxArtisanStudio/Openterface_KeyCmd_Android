package com.openterface.terminal;

import android.util.Log;

import com.openterface.keymod.BuildConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * State machine parser for BLE-Eth frames.
 * Ported from Python ble_eth_tunnel.py FrameParser.
 *
 * Frame format:
 *   [0] 0x57 - sync byte 1
 *   [1] 0xAB - sync byte 2
 *   [2] addr - target address
 *   [3] cmd  - command byte
 *   [4] len  - payload length
 *   [5..4+len] payload
 *   [5+len] checksum (sum of all preceding bytes mod 256)
 */
public class FrameParser {

    private static final int FRAME_HEAD1 = 0x57;
    private static final int FRAME_HEAD2 = 0xAB;
    private static final int FRAME_HEAD_LEN = 5;
    private static final int MAX_PAYLOAD_LEN = 249;

    // Parser states
    private static final int STATE_WAIT_HEAD1 = 0;
    private static final int STATE_WAIT_HEAD2 = 1;
    private static final int STATE_WAIT_ADDR = 2;
    private static final int STATE_WAIT_CMD = 3;
    private static final int STATE_WAIT_LEN = 4;
    private static final int STATE_WAIT_PAYLOAD = 5;
    private static final int STATE_WAIT_CHECKSUM = 6;

    private int state = STATE_WAIT_HEAD1;
    private byte[] buf = new byte[MAX_PAYLOAD_LEN + FRAME_HEAD_LEN + 1];
    private int bufLen = 0;
    private int payloadLen = 0;
    private int checksum = 0;

    private final Deque<ParsedFrame> receivedFrames = new ArrayDeque<>();

    /** Feed raw bytes into the parser. Call handleIncomingFrames() to consume parsed frames. */
    public void feed(byte[] data) {
        for (byte b : data) {
            process(b & 0xFF);
        }
        if (!receivedFrames.isEmpty()) {
            android.util.Log.d("FrameParser", "feed: " + receivedFrames.size() + " frames ready");
        }
    }

    /** Feed a single byte into the parser. */
    public void feedByte(int b) {
        process(b & 0xFF);
    }

    private void process(int b) {
        switch (state) {
            case STATE_WAIT_HEAD1:
                if (b == FRAME_HEAD1) {
                    android.util.Log.d("FrameParser", "Found HEAD1 (0x57), waiting for HEAD2");
                    bufLen = 0;
                    buf[bufLen++] = (byte) b;
                    checksum = b;
                    state = STATE_WAIT_HEAD2;
                }
                break;

            case STATE_WAIT_HEAD2:
                if (b == FRAME_HEAD2) {
                    buf[bufLen++] = (byte) b;
                    checksum += b;
                    state = STATE_WAIT_ADDR;
                } else if (b == FRAME_HEAD1) {
                    // Restart sync
                    bufLen = 0;
                    buf[bufLen++] = (byte) b;
                    checksum = b;
                    state = STATE_WAIT_HEAD2;
                } else {
                    state = STATE_WAIT_HEAD1;
                }
                break;

            case STATE_WAIT_ADDR:
                buf[bufLen++] = (byte) b;
                checksum += b;
                state = STATE_WAIT_CMD;
                break;

            case STATE_WAIT_CMD:
                buf[bufLen++] = (byte) b;
                checksum += b;
                state = STATE_WAIT_LEN;
                break;

            case STATE_WAIT_LEN:
                buf[bufLen++] = (byte) b;
                checksum += b;
                payloadLen = b;
                if (payloadLen == 0) {
                    state = STATE_WAIT_CHECKSUM;
                } else if (payloadLen > MAX_PAYLOAD_LEN) {
                    state = STATE_WAIT_HEAD1; // Invalid length, reset
                } else {
                    state = STATE_WAIT_PAYLOAD;
                }
                break;

            case STATE_WAIT_PAYLOAD:
                buf[bufLen++] = (byte) b;
                checksum += b;
                if (bufLen >= FRAME_HEAD_LEN + payloadLen) {
                    state = STATE_WAIT_CHECKSUM;
                }
                break;

            case STATE_WAIT_CHECKSUM:
                int expected = checksum & 0xFF;
                if (b == expected) {
                    int cmd = buf[3] & 0xFF;
                    int addr = buf[2] & 0xFF;
                    byte[] payload = new byte[payloadLen];
                    System.arraycopy(buf, 5, payload, 0, payloadLen);
                    if (BuildConfig.DEBUG) {
                        Log.d("FrameParser", "Checksum OK! Frame: addr=0x" + Integer.toHexString(addr)
                                + " cmd=0x" + Integer.toHexString(cmd) + " payloadLen=" + payloadLen);
                    }
                    receivedFrames.add(new ParsedFrame(addr, cmd, payload));
                } else {
                    android.util.Log.w("FrameParser", "Checksum mismatch! Expected=0x" + Integer.toHexString(expected)
                            + " Got=0x" + Integer.toHexString(b) + " Frame discarded");
                }
                state = STATE_WAIT_HEAD1;
                break;
        }
    }

    public boolean isEmpty() { return receivedFrames.isEmpty(); }
    public ParsedFrame pop() { return receivedFrames.poll(); }
    public int pendingCount() { return receivedFrames.size(); }

    /** Parsed BLE-Eth frame. */
    public static class ParsedFrame {
        public final int addr;
        public final int cmd;
        public final byte[] payload;

        public ParsedFrame(int addr, int cmd, byte[] payload) {
            this.addr = addr;
            this.cmd = cmd;
            this.payload = payload;
        }
    }
}
