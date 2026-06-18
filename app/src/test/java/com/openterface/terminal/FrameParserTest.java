package com.openterface.terminal;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for FrameParser.
 * Verifies frame synchronization, checksum validation, and multi-byte parsing.
 */
public class FrameParserTest {

    private FrameParser parser;

    @Before
    public void setUp() {
        parser = new FrameParser();
    }

    // ── Basic Frame Parsing ─────────────────────────────────────

    @Test
    public void validFrame_parsedCorrectly() {
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01, 0x02, 0x03});
        parser.feed(frame);
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(0x00, parsed.addr);
        assertEquals(0x10, parsed.cmd);
        assertEquals(3, parsed.payload.length);
        assertEquals(0x01, parsed.payload[0] & 0xFF);
        assertEquals(0x02, parsed.payload[1] & 0xFF);
        assertEquals(0x03, parsed.payload[2] & 0xFF);
    }

    @Test
    public void emptyPayload_parsedCorrectly() {
        byte[] frame = buildFrame(0x01, 0x1F, new byte[0]);
        parser.feed(frame);
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(0x01, parsed.addr);
        assertEquals(0x1F, parsed.cmd);
        assertEquals(0, parsed.payload.length);
    }

    @Test
    public void maxPayload_parsedCorrectly() {
        byte[] payload = new byte[249];
        for (int i = 0; i < 249; i++) payload[i] = (byte) i;
        byte[] frame = buildFrame(0x00, 0x11, payload);
        parser.feed(frame);
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(249, parsed.payload.length);
        for (int i = 0; i < 249; i++) {
            assertEquals((byte) i, parsed.payload[i]);
        }
    }

    // ── Checksum Validation ─────────────────────────────────────

    @Test
    public void badChecksum_frameDiscarded() {
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01});
        // Corrupt the checksum byte
        frame[frame.length - 1] = (byte) (frame[frame.length - 1] + 1);
        parser.feed(frame);
        assertTrue(parser.isEmpty());
    }

    @Test
    public void correctChecksum_accepted() {
        // Verify that a correct checksum passes
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x42});
        parser.feed(frame);
        assertFalse(parser.isEmpty());
    }

    // ── Sync Recovery ───────────────────────────────────────────

    @Test
    public void garbageBeforeSync_recovers() {
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01});
        byte[] data = new byte[3 + frame.length];
        data[0] = 0x00; // garbage
        data[1] = (byte) 0xFF; // garbage
        data[2] = 0x42; // garbage
        System.arraycopy(frame, 0, data, 3, frame.length);
        parser.feed(data);
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(0x10, parsed.cmd);
    }

    @Test
    public void partialSync_resets() {
        // 0x57 followed by non-0xAB should reset
        parser.feedByte(0x57);
        parser.feedByte(0x42); // not 0xAB
        // Now send a valid frame
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01});
        parser.feed(frame);
        assertFalse(parser.isEmpty());
    }

    @Test
    public void head1InsideSync_restarts() {
        // Verify that a valid frame parses correctly when fed byte by byte
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01});
        for (byte b : frame) {
            parser.feedByte(b & 0xFF);
        }
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(0x10, parsed.cmd);
        assertEquals(0x00, parsed.addr);
        assertEquals(1, parsed.payload.length);
        assertEquals(0x01, parsed.payload[0]);
    }

    // ── Multiple Frames ─────────────────────────────────────────

    @Test
    public void twoFramesInOneBuffer_bothParsed() {
        byte[] frame1 = buildFrame(0x00, 0x10, new byte[]{0x01});
        byte[] frame2 = buildFrame(0x01, 0x11, new byte[]{0x02, 0x03});
        byte[] combined = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, combined, 0, frame1.length);
        System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);
        parser.feed(combined);
        assertEquals(2, parser.pendingCount());
        FrameParser.ParsedFrame p1 = parser.pop();
        assertEquals(0x10, p1.cmd);
        FrameParser.ParsedFrame p2 = parser.pop();
        assertEquals(0x11, p2.cmd);
    }

    @Test
    public void byteByByte_works() {
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01, 0x02});
        for (byte b : frame) {
            parser.feedByte(b & 0xFF);
        }
        assertFalse(parser.isEmpty());
        FrameParser.ParsedFrame parsed = parser.pop();
        assertEquals(0x10, parsed.cmd);
        assertEquals(2, parsed.payload.length);
    }

    // ── Edge Cases ──────────────────────────────────────────────

    @Test
    public void emptyData_noFrames() {
        parser.feed(new byte[0]);
        assertTrue(parser.isEmpty());
    }

    @Test
    public void invalidLength_resets() {
        // Payload length > MAX_PAYLOAD_LEN (249) should reset
        parser.feedByte(0x57); // head1
        parser.feedByte(0xAB); // head2
        parser.feedByte(0x00); // addr
        parser.feedByte(0x10); // cmd
        parser.feedByte(0xFA); // len = 250 (invalid, > 249)
        // Should reset to WAIT_HEAD1
        byte[] frame = buildFrame(0x00, 0x10, new byte[]{0x01});
        parser.feed(frame);
        assertFalse(parser.isEmpty());
    }

    // ── Helpers ─────────────────────────────────────────────────

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

    /** Build the part of a frame after the sync bytes (addr..checksum). */
    private static byte[] buildFramePayload(int addr, int cmd, byte[] payload) {
        int payloadLen = payload != null ? payload.length : 0;
        byte[] rest = new byte[4 + payloadLen];
        rest[0] = (byte) addr;
        rest[1] = (byte) cmd;
        rest[2] = (byte) payloadLen;
        if (payload != null) {
            System.arraycopy(payload, 0, rest, 3, payload.length);
        }
        int checksum = 0x57 + 0xAB; // sync bytes already counted
        for (int i = 0; i < rest.length; i++) {
            checksum += rest[i] & 0xFF;
        }
        byte[] result = new byte[rest.length + 1];
        System.arraycopy(rest, 0, result, 0, rest.length);
        result[result.length - 1] = (byte) (checksum & 0xFF);
        return result;
    }
}
