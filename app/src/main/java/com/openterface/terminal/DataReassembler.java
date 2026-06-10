package com.openterface.terminal;

import java.io.ByteArrayOutputStream;

/**
 * Reassembles fragmented DATA frames into complete payloads.
 * Ported from Python ble_eth_tunnel.py DataReassembler.
 *
 * Fragment header (3 bytes):
 *   [0] flags:
 *     bit 7 (0x80) = FRAG_MORE: more fragments follow
 *     bit 6 (0x40) = FRAG_FIRST: first fragment of a message
 *     bits 0-3     = total fragment count
 *   [1] seq: fragment sequence number (0-based)
 *   [2] connId: connection ID
 */
public class DataReassembler {

    private static final int FRAG_HEADER_LEN = 3;
    private static final byte FRAG_MORE = (byte) 0x80;
    private static final byte FRAG_FIRST = 0x40;
    private static final int FRAG_COUNT_MASK = 0x0F;

    private boolean active = false;
    private int connId = 0;
    private int totalFrags = 0;
    private int nextSeq = 0;
    private ByteArrayOutputStream buf = new ByteArrayOutputStream();

    /**
     * Feed a data fragment payload (the CMD_DATA payload, not the full frame).
     * @return ReassembledData when the last fragment is received, null otherwise.
     */
    public ReassembledData feed(byte[] payload) {
        if (payload == null || payload.length < FRAG_HEADER_LEN) return null;

        int flags = payload[0] & 0xFF;
        int seq = payload[1] & 0xFF;
        connId = payload[2] & 0xFF;
        int fragDataLen = payload.length - FRAG_HEADER_LEN;

        int total = flags & FRAG_COUNT_MASK;
        if (total == 0) total = 1; // single fragment

        // Check if this is the first fragment
        if ((flags & FRAG_FIRST) != 0) {
            active = true;
            this.totalFrags = total;
            nextSeq = 0;
            buf = new ByteArrayOutputStream();
        }

        if (!active) return null;

        // Check sequence number
        if (seq != nextSeq) {
            active = false;
            return null; // out of order, discard
        }

        // Append fragment data
        buf.write(payload, FRAG_HEADER_LEN, fragDataLen);
        nextSeq++;

        // Check if this is the last fragment
        if ((flags & FRAG_MORE) == 0) {
            active = false;
            return new ReassembledData(connId, buf.toByteArray());
        }
        return null;
    }

    /** Reassembled data result. */
    public static class ReassembledData {
        public final int connId;
        public final byte[] data;

        public ReassembledData(int connId, byte[] data) {
            this.connId = connId;
            this.data = data;
        }
    }
}
