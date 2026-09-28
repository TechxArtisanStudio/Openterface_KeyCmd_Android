package com.openterface.terminal;

import android.util.Log;

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

    private static final String TAG = "DataReassembler";

    private static final int FRAG_HEADER_LEN = 3;
    private static final byte FRAG_MORE = (byte) 0x80;
    private static final byte FRAG_FIRST = 0x40;
    private static final int FRAG_COUNT_MASK = 0x0F;

    private volatile boolean active = false;
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

        boolean isFirst = (flags & FRAG_FIRST) != 0;
        boolean hasMore = (flags & FRAG_MORE) != 0;

        // Sanity check: seq must be within [0, total-1].
        // If seq >= total, the firmware's fragment metadata is inconsistent.
        // This catches cases like total=1,seq=2 where a middle fragment is
        // mislabeled as a single-fragment message.
        if (seq >= total) {
            Log.w(TAG, "Fragment rejected: seq=" + seq + " >= total=" + total
                    + " connId=" + connId + " isFirst=" + isFirst
                    + " hasMore=" + hasMore + " fragDataLen=" + fragDataLen
                    + " — inconsistent fragment metadata, discarding");
            active = false; // reset any in-progress reassembly
            return null;
        }

        // Check if this is the first fragment
        if (isFirst) {
            if (active) {
                // Previous reassembly was incomplete — log the abandonment
                Log.w(TAG, "FRAG_FIRST received while reassembly active: "
                        + "discarding " + nextSeq + "/" + totalFrags
                        + " fragments (" + buf.size() + " bytes buffered) for connId=" + connId);
            }
            active = true;
            this.totalFrags = total;
            nextSeq = 0;
            buf = new ByteArrayOutputStream();
            Log.v(TAG, "Reassembly started: connId=" + connId
                    + " totalFrags=" + total + " fragDataLen=" + fragDataLen);
        }

        if (!active) {
            if (seq > 0 && total > 1) {
                // Orphan fragment: FRAG_FIRST (and possibly earlier fragments)
                // were lost but we have a continuation. Start reassembly from
                // this fragment so the remaining data is not silently discarded.
                Log.w(TAG, "Orphan fragment received (seq=" + seq + "/" + total
                        + " connId=" + connId + "). Starting reassembly from seq=" + seq
                        + " — first " + seq + " fragment(s) were lost, data will be truncated");
                active = true;
                this.totalFrags = total;
                nextSeq = seq;
                buf = new ByteArrayOutputStream();
            } else {
                Log.w(TAG, "Fragment dropped: reassembly not active. "
                        + "seq=" + seq + " connId=" + connId
                        + " isFirst=" + isFirst + " hasMore=" + hasMore
                        + " total=" + total + " fragDataLen=" + fragDataLen
                        + " — waiting for FRAG_FIRST");
                return null;
            }
        }

        // Check sequence number
        if (seq != nextSeq) {
            Log.e(TAG, "SEQUENCE MISMATCH: expected seq=" + nextSeq
                    + " but got seq=" + seq + " connId=" + connId
                    + " totalFrags=" + totalFrags
                    + " buffered=" + buf.size() + " bytes"
                    + " — ABORTING reassembly (data lost!)");
            active = false;
            return null; // out of order, discard
        }

        // Append fragment data
        buf.write(payload, FRAG_HEADER_LEN, fragDataLen);
        nextSeq++;

        // Check if this is the last fragment
        if (!hasMore) {
            active = false;
            Log.i(TAG, "Reassembly complete: connId=" + connId
                    + " fragments=" + nextSeq + "/" + totalFrags
                    + " totalBytes=" + buf.size());
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
