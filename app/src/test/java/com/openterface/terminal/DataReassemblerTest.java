package com.openterface.terminal;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for DataReassembler.
 * Verifies fragment reassembly, sequence tracking, and error handling.
 */
public class DataReassemblerTest {

    private DataReassembler reassembler;

    @Before
    public void setUp() {
        reassembler = new DataReassembler();
    }

    // ── Single Fragment (No Fragmentation) ──────────────────────

    @Test
    public void singleFragment_returnsCompleteData() {
        byte[] payload = buildPayload(
            (byte) (DataReassemblerTest.FRAG_FIRST), // flags: first, no more, count=1
            (byte) 0x00,                              // seq 0
            (byte) 0x01,                              // connId 1
            "Hello".getBytes()
        );
        DataReassembler.ReassembledData result = reassembler.feed(payload);
        assertNotNull(result);
        assertEquals(1, result.connId);
        assertEquals("Hello", new String(result.data));
    }

    @Test
    public void singleFragment_countZero_treatedAsOne() {
        // When total count is 0, it means single fragment
        byte[] payload = buildPayload(
            (byte) (FRAG_FIRST), // flags: first, no more, count=0
            (byte) 0x00,
            (byte) 0x02,
            "Data".getBytes()
        );
        DataReassembler.ReassembledData result = reassembler.feed(payload);
        assertNotNull(result);
        assertEquals(2, result.connId);
        assertEquals("Data", new String(result.data));
    }

    // ── Multi-Fragment ──────────────────────────────────────────

    @Test
    public void twoFragments_reassemblesInOrder() {
        // Fragment 0: first, more to come, total=2
        byte[] frag0 = buildPayload(
            (byte) (FRAG_FIRST | FRAG_MORE | 2),
            (byte) 0x00,
            (byte) 0x03,
            "Hello ".getBytes()
        );
        DataReassembler.ReassembledData r0 = reassembler.feed(frag0);
        assertNull(r0); // not complete yet

        // Fragment 1: not first, no more, total=2
        byte[] frag1 = buildPayload(
            (byte) (2), // flags: not first, no more, count=2
            (byte) 0x01,
            (byte) 0x03,
            "World".getBytes()
        );
        DataReassembler.ReassembledData r1 = reassembler.feed(frag1);
        assertNotNull(r1);
        assertEquals(3, r1.connId);
        assertEquals("Hello World", new String(r1.data));
    }

    @Test
    public void threeFragments_reassemblesAll() {
        byte[] frag0 = buildPayload(
            (byte) (FRAG_FIRST | FRAG_MORE | 3),
            (byte) 0x00, (byte) 0x01, "ABC".getBytes());
        byte[] frag1 = buildPayload(
            (byte) (FRAG_MORE | 3),
            (byte) 0x01, (byte) 0x01, "DEF".getBytes());
        byte[] frag2 = buildPayload(
            (byte) (3), // last
            (byte) 0x02, (byte) 0x01, "GHI".getBytes());

        assertNull(reassembler.feed(frag0));
        assertNull(reassembler.feed(frag1));
        DataReassembler.ReassembledData result = reassembler.feed(frag2);
        assertNotNull(result);
        assertEquals("ABCDEFGHI", new String(result.data));
    }

    // ── Out of Order ────────────────────────────────────────────

    @Test
    public void outOfOrderSequence_discards() {
        // Fragment 0: first, more, total=2
        byte[] frag0 = buildPayload(
            (byte) (FRAG_FIRST | FRAG_MORE | 2),
            (byte) 0x00, (byte) 0x01, "Part1".getBytes());
        assertNull(reassembler.feed(frag0));

        // Fragment 2 (out of order — expected seq 1)
        byte[] frag2 = buildPayload(
            (byte) (2),
            (byte) 0x02, (byte) 0x01, "Part3".getBytes());
        DataReassembler.ReassembledData result = reassembler.feed(frag2);
        assertNull(result); // discarded, not complete
    }

    @Test
    public void fragmentWithoutFirst_discards() {
        // Fragment without FRAG_FIRST flag when no active reassembly
        byte[] frag = buildPayload(
            (byte) (FRAG_MORE | 2),
            (byte) 0x01, (byte) 0x01, "Data".getBytes());
        DataReassembler.ReassembledData result = reassembler.feed(frag);
        assertNull(result);
    }

    // ── New First Fragment Resets ───────────────────────────────

    @Test
    public void newFirstFragment_resetsPreviousState() {
        // Start first stream
        byte[] frag0a = buildPayload(
            (byte) (FRAG_FIRST | FRAG_MORE | 2),
            (byte) 0x00, (byte) 0x01, "Old".getBytes());
        assertNull(reassembler.feed(frag0a));

        // New first fragment resets
        byte[] frag0b = buildPayload(
            (byte) (FRAG_FIRST), // single
            (byte) 0x00, (byte) 0x02, "New".getBytes());
        DataReassembler.ReassembledData result = reassembler.feed(frag0b);
        assertNotNull(result);
        assertEquals(2, result.connId);
        assertEquals("New", new String(result.data));
    }

    // ── Edge Cases ──────────────────────────────────────────────

    @Test
    public void nullPayload_returnsNull() {
        assertNull(reassembler.feed(null));
    }

    @Test
    public void tooShortPayload_returnsNull() {
        byte[] payload = new byte[]{0x01, 0x02}; // less than FRAG_HEADER_LEN (3)
        assertNull(reassembler.feed(payload));
    }

    @Test
    public void emptyDataPayload_works() {
        byte[] payload = buildPayload(
            (byte) (FRAG_FIRST),
            (byte) 0x00, (byte) 0x01, new byte[0]);
        DataReassembler.ReassembledData result = reassembler.feed(payload);
        assertNotNull(result);
        assertEquals(0, result.data.length);
    }

    // ── Constants (package-private for test access) ─────────────

    static final byte FRAG_MORE = (byte) 0x80;
    static final byte FRAG_FIRST = 0x40;

    private static byte[] buildPayload(byte flags, byte seq, byte connId, byte[] data) {
        byte[] payload = new byte[3 + data.length];
        payload[0] = flags;
        payload[1] = seq;
        payload[2] = connId;
        System.arraycopy(data, 0, payload, 3, data.length);
        return payload;
    }
}
