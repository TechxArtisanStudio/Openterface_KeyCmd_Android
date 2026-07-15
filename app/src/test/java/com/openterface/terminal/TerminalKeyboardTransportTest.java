package com.openterface.terminal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for TerminalKeyboardTransport.
 * Verifies that HID key events are correctly converted to ANSI/xterm escape sequences.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalKeyboardTransportTest {

    private static final int MOD_CTRL  = 0x01;
    private static final int MOD_SHIFT = 0x02;
    private static final int MOD_ALT   = 0x04;

    private static final int HID_KEY_A = 0x04;
    private static final int HID_KEY_Z = 0x1D;
    private static final int HID_KEY_1 = 0x1E;
    private static final int HID_KEY_0 = 0x27;

    private ByteArrayOutputStream out;
    private TerminalKeyboardTransport transport;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        transport = new TerminalKeyboardTransport(out);
    }

    // ── Helper methods ────────────────────────────────────────

    private byte[] sendAndCapture(int mod, int keyCode) {
        out.reset();
        transport.sendKey(mod, keyCode);
        return out.toByteArray();
    }

    private void assertBytesEquals(byte[] actual, int... expected) {
        assertEquals("Length mismatch", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Byte mismatch at index " + i, (byte) expected[i], actual[i]);
        }
    }

    private void assertBytesEquals(byte[] actual, byte[] expected) {
        assertArrayEquals(expected, actual);
    }

    // ══════════════════════════════════════════════════════════════
    // A. Letters (11 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void plainLetter_a() {
        byte[] result = sendAndCapture(0, 0x04);
        assertBytesEquals(result, 0x61); // 'a'
    }

    @Test
    public void plainLetter_z() {
        byte[] result = sendAndCapture(0, 0x1D);
        assertBytesEquals(result, 0x7A); // 'z'
    }

    @Test
    public void plainLetter_m() {
        byte[] result = sendAndCapture(0, 0x10);
        assertBytesEquals(result, 0x6D); // 'm'
    }

    @Test
    public void shiftedLetter_A() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x04);
        assertBytesEquals(result, 0x41); // 'A'
    }

    @Test
    public void shiftedLetter_Z() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x1D);
        assertBytesEquals(result, 0x5A); // 'Z'
    }

    @Test
    public void ctrlLetter_ctrlA_sends0x01() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x04);
        assertBytesEquals(result, 0x01); // SOH
    }

    @Test
    public void ctrlLetter_ctrlZ_sends0x1A() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x1D);
        assertBytesEquals(result, 0x1A); // SUB
    }

    @Test
    public void ctrlShiftLetter_xtermSequence() {
        byte[] result = sendAndCapture(MOD_CTRL | MOD_SHIFT, 0x04);
        // Expected: ESC[65;6~
        assertBytesEquals(result, 0x1B, '[', '6', '5', ';', '6', '~');
    }

    @Test
    public void altLetter_metaPrefix() {
        byte[] result = sendAndCapture(MOD_ALT, 0x04);
        assertBytesEquals(result, 0x1B, 0x61); // ESC + 'a'
    }

    @Test
    public void altShiftLetter_metaPrefixUpper() {
        byte[] result = sendAndCapture(MOD_ALT | MOD_SHIFT, 0x04);
        assertBytesEquals(result, 0x1B, 0x41); // ESC + 'A'
    }

    @Test
    public void allLetters_produceCorrectAscii() {
        for (int hid = HID_KEY_A; hid <= HID_KEY_Z; hid++) {
            byte[] result = sendAndCapture(0, hid);
            assertEquals("Length mismatch for HID " + hid, 1, result.length);
            int expectedAscii = hid - HID_KEY_A + 0x61; // 'a' = 0x61
            assertEquals("Wrong ASCII for HID " + hid, (byte) expectedAscii, result[0]);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // B. Digits (4 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void plainDigit_1() {
        byte[] result = sendAndCapture(0, 0x1E);
        assertBytesEquals(result, 0x31); // '1'
    }

    @Test
    public void plainDigit_9() {
        byte[] result = sendAndCapture(0, 0x26);
        assertBytesEquals(result, 0x39); // '9'
    }

    @Test
    public void plainDigit_0() {
        byte[] result = sendAndCapture(0, 0x27);
        assertBytesEquals(result, 0x30); // '0'
    }

    @Test
    public void allDigits_produceCorrectAscii() {
        int[] expectedDigits = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
        for (int i = 0; i < 10; i++) {
            int hid = HID_KEY_1 + i;
            byte[] result = sendAndCapture(0, hid);
            assertEquals("Length mismatch for digit " + i, 1, result.length);
            assertEquals("Wrong ASCII for digit " + i, (byte) (expectedDigits[i] + 0x30), result[0]);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // C. Control keys (5 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void enter_sendsCR() {
        byte[] result = sendAndCapture(0, 0x28);
        assertBytesEquals(result, 0x0D);
    }

    @Test
    public void esc_sendsESC() {
        byte[] result = sendAndCapture(0, 0x29);
        assertBytesEquals(result, 0x1B);
    }

    @Test
    public void backspace_sendsDEL() {
        byte[] result = sendAndCapture(0, 0x2A);
        assertBytesEquals(result, 0x7F);
    }

    @Test
    public void tab_sendsHT() {
        byte[] result = sendAndCapture(0, 0x2B);
        assertBytesEquals(result, 0x09);
    }

    @Test
    public void space_sendsSP() {
        byte[] result = sendAndCapture(0, 0x2C);
        assertBytesEquals(result, 0x20);
    }

    // ══════════════════════════════════════════════════════════════
    // D. F-keys (6 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void f1_sendsSS3_P() {
        byte[] result = sendAndCapture(0, 0x3A);
        assertBytesEquals(result, 0x1B, 'O', 'P');
    }

    @Test
    public void f4_sendsSS3_S() {
        byte[] result = sendAndCapture(0, 0x3D);
        assertBytesEquals(result, 0x1B, 'O', 'S');
    }

    @Test
    public void f5_sendsCSI_15tilde() {
        byte[] result = sendAndCapture(0, 0x3E);
        assertBytesEquals(result, 0x1B, '[', '1', '5', '~');
    }

    @Test
    public void f12_sendsCSI_24tilde() {
        byte[] result = sendAndCapture(0, 0x45);
        assertBytesEquals(result, 0x1B, '[', '2', '4', '~');
    }

    @Test
    public void allFKeys_produceCorrectSequences() {
        byte[][] expected = {
            {0x1B, 'O', 'P'}, {0x1B, 'O', 'Q'}, {0x1B, 'O', 'R'}, {0x1B, 'O', 'S'},     // F1-F4
            {0x1B, '[', '1', '5', '~'}, {0x1B, '[', '1', '7', '~'}, {0x1B, '[', '1', '8', '~'}, {0x1B, '[', '1', '9', '~'}, // F5-F8
            {0x1B, '[', '2', '0', '~'}, {0x1B, '[', '2', '1', '~'}, {0x1B, '[', '2', '3', '~'}, {0x1B, '[', '2', '4', '~'}  // F9-F12
        };
        for (int i = 0; i < 12; i++) {
            int hid = 0x3A + i;
            byte[] result = sendAndCapture(0, hid);
            assertBytesEquals(result, expected[i]);
        }
    }

    @Test
    public void f5_ctrlXtermModifier() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x3E);
        // Expected: ESC[15;5~
        assertBytesEquals(result, 0x1B, '[', '1', '5', ';', '5', '~');
    }

    // ══════════════════════════════════════════════════════════════
    // E. Arrow keys (6 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void arrowUp_plain() {
        byte[] result = sendAndCapture(0, 0x52);
        assertBytesEquals(result, 0x1B, '[', 'A');
    }

    @Test
    public void arrowDown_plain() {
        byte[] result = sendAndCapture(0, 0x51);
        assertBytesEquals(result, 0x1B, '[', 'B');
    }

    @Test
    public void arrowLeft_plain() {
        byte[] result = sendAndCapture(0, 0x50);
        assertBytesEquals(result, 0x1B, '[', 'D');
    }

    @Test
    public void arrowRight_plain() {
        byte[] result = sendAndCapture(0, 0x4F);
        assertBytesEquals(result, 0x1B, '[', 'C');
    }

    @Test
    public void arrowUp_ctrlXtermModifier() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x52);
        // Expected: ESC[1;5A
        assertBytesEquals(result, 0x1B, '[', '1', ';', '5', 'A');
    }

    @Test
    public void arrowUp_shiftXtermModifier() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x52);
        // Expected: ESC[1;3A
        assertBytesEquals(result, 0x1B, '[', '1', ';', '3', 'A');
    }

    // ══════════════════════════════════════════════════════════════
    // F. Navigation keys (6 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void home_plain() {
        byte[] result = sendAndCapture(0, 0x4A);
        assertBytesEquals(result, 0x1B, '[', 'H');
    }

    @Test
    public void end_plain() {
        byte[] result = sendAndCapture(0, 0x4D);
        assertBytesEquals(result, 0x1B, '[', 'F');
    }

    @Test
    public void pageUp_plain() {
        byte[] result = sendAndCapture(0, 0x4B);
        assertBytesEquals(result, 0x1B, '[', '5', '~');
    }

    @Test
    public void pageDown_plain() {
        byte[] result = sendAndCapture(0, 0x4E);
        assertBytesEquals(result, 0x1B, '[', '6', '~');
    }

    @Test
    public void delete_plain() {
        byte[] result = sendAndCapture(0, 0x4C);
        assertBytesEquals(result, 0x1B, '[', '3', '~');
    }

    @Test
    public void delete_ctrlXtermModifier() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x4C);
        // Expected: ESC[3;5~
        assertBytesEquals(result, 0x1B, '[', '3', ';', '5', '~');
    }

    // ══════════════════════════════════════════════════════════════
    // G. Symbol keys (8 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void symbol_dash_plain() {
        byte[] result = sendAndCapture(0, 0x2D);
        assertBytesEquals(result, (byte) '-');
    }

    @Test
    public void symbol_dash_shifted() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x2D);
        assertBytesEquals(result, (byte) '_');
    }

    @Test
    public void symbol_semicolon_shifted() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x33);
        assertBytesEquals(result, (byte) ':');
    }

    @Test
    public void symbol_slash_shifted() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x38);
        assertBytesEquals(result, (byte) '?');
    }

    @Test
    public void allPlainSymbols_produceCorrectAscii() {
        int[] symbolHids = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
        byte[] expectedChars = {'-', '=', '[', ']', '\\', ';', '\'', '`', ',', '.', '/'};
        for (int i = 0; i < symbolHids.length; i++) {
            byte[] result = sendAndCapture(0, symbolHids[i]);
            assertBytesEquals(result, new byte[]{expectedChars[i]});
        }
    }

    @Test
    public void allShiftedSymbols_produceCorrectAscii() {
        int[] symbolHids = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
        byte[] expectedChars = {'_', '+', '{', '}', '|', ':', '"', '~', '<', '>', '?'};
        for (int i = 0; i < symbolHids.length; i++) {
            byte[] result = sendAndCapture(MOD_SHIFT, symbolHids[i]);
            assertBytesEquals(result, new byte[]{expectedChars[i]});
        }
    }

    @Test
    public void symbol_ctrlXtermModifier() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x2D);
        // Expected: ESC[1;5- (xterm modifier for Ctrl+-)
        assertBytesEquals(result, 0x1B, '[', '1', ';', '5', '-');
    }

    @Test
    public void symbol_altPrefix() {
        byte[] result = sendAndCapture(MOD_ALT, 0x2D);
        assertBytesEquals(result, 0x1B, (byte) '-');
    }

    // ══════════════════════════════════════════════════════════════
    // H. xterm modifier encoding (5 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void xtermModifier_ctrlArrow() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x52);
        // Expected: ESC[1;5A (mod = 1 + 4 = 5)
        assertBytesEquals(result, 0x1B, '[', '1', ';', '5', 'A');
    }

    @Test
    public void xtermModifier_shiftHome() {
        byte[] result = sendAndCapture(MOD_SHIFT, 0x4A);
        // Expected: ESC[1;3H (mod = 1 + 2 = 3)
        assertBytesEquals(result, 0x1B, '[', '1', ';', '3', 'H');
    }

    @Test
    public void xtermModifier_ctrlShiftDelete() {
        byte[] result = sendAndCapture(MOD_CTRL | MOD_SHIFT, 0x4C);
        // Expected: ESC[3;7~ (mod = 1 + 2 + 4 = 7)
        assertBytesEquals(result, 0x1B, '[', '3', ';', '7', '~');
    }

    @Test
    public void xtermModifier_ctrlPageUp() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x4B);
        // Expected: ESC[5;5~
        assertBytesEquals(result, 0x1B, '[', '5', ';', '5', '~');
    }

    @Test
    public void xtermModifier_f1f4_notModified() {
        byte[] result = sendAndCapture(MOD_CTRL, 0x3A);
        // F1 is SS3 sequence (ESC O P), not modified by xterm
        assertBytesEquals(result, 0x1B, 'O', 'P');
    }

    // ══════════════════════════════════════════════════════════════
    // I. disconnect and boundary (6 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void disconnect_isConnectedReturnsFalse() {
        assertTrue(transport.isConnected());
        transport.disconnect();
        assertFalse(transport.isConnected());
    }

    @Test
    public void disconnect_subsequentWritesSilent() {
        transport.disconnect();
        transport.sendKey(0, 0x04);
        assertEquals(0, out.size());
    }

    @Test
    public void nullOutput_isConnectedFalse() {
        TerminalKeyboardTransport nullTransport = new TerminalKeyboardTransport(null);
        assertFalse(nullTransport.isConnected());
    }

    @Test
    public void nullOutput_sendKeyDoesNotCrash() {
        TerminalKeyboardTransport nullTransport = new TerminalKeyboardTransport(null);
        nullTransport.sendKey(0, 0x04);
    }

    @Test
    public void sendAllKeysReleased_isNoOp() {
        transport.sendAllKeysReleased();
        assertEquals(0, out.size());
    }

    @Test
    public void unmappedKey_isIgnored() {
        byte[] result = sendAndCapture(0, 0xFF);
        assertEquals(0, result.length);
    }

    // ══════════════════════════════════════════════════════════════
    // J. Alt prefix / Meta key (3 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void alt_enter() {
        byte[] result = sendAndCapture(MOD_ALT, 0x28);
        assertBytesEquals(result, 0x1B, 0x0D);
    }

    @Test
    public void alt_arrowUp() {
        byte[] result = sendAndCapture(MOD_ALT, 0x52);
        // Expected: ESC ESC[A
        assertBytesEquals(result, 0x1B, 0x1B, '[', 'A');
    }

    @Test
    public void alt_f5() {
        byte[] result = sendAndCapture(MOD_ALT, 0x3E);
        // Expected: ESC ESC[15~
        assertBytesEquals(result, 0x1B, 0x1B, '[', '1', '5', '~');
    }

    // ══════════════════════════════════════════════════════════════
    // K. Integration tests (3 tests)
    // ══════════════════════════════════════════════════════════════

    @Test
    public void integration_writtenBytesReachOutputStream() {
        ByteArrayOutputStream bridge = new ByteArrayOutputStream();
        TerminalKeyboardTransport bridgeTransport = new TerminalKeyboardTransport(bridge);

        bridgeTransport.sendKey(0, 0x04);       // 'a'
        bridgeTransport.sendKey(0, 0x05);       // 'b'
        bridgeTransport.sendKey(0, 0x28);       // Enter

        byte[] allWritten = bridge.toByteArray();
        assertEquals(3, allWritten.length);
        assertEquals((byte) 'a', allWritten[0]);
        assertEquals((byte) 'b', allWritten[1]);
        assertEquals((byte) 0x0D, allWritten[2]);
    }

    @Test
    public void integration_escapeSequencesReachOutputStreamIntact() {
        ByteArrayOutputStream bridge = new ByteArrayOutputStream();
        TerminalKeyboardTransport bridgeTransport = new TerminalKeyboardTransport(bridge);

        bridgeTransport.sendKey(0, 0x52); // Up arrow

        byte[] allWritten = bridge.toByteArray();
        assertEquals(3, allWritten.length);
        assertEquals((byte) 0x1B, allWritten[0]); // ESC
        assertEquals((byte) '[', allWritten[1]);
        assertEquals((byte) 'A', allWritten[2]);
    }

    @Test
    public void disconnect_duringConcurrentWrites_doesNotThrow() throws Exception {
        final ByteArrayOutputStream bridge = new ByteArrayOutputStream();
        final TerminalKeyboardTransport bridgeTransport = new TerminalKeyboardTransport(bridge);

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                try {
                    bridgeTransport.sendKey(0, HID_KEY_A + (i % 26));
                } catch (Exception e) {
                    fail("sendKey threw: " + e);
                }
            }
        });

        writer.start();
        Thread.sleep(5);
        bridgeTransport.disconnect();
        writer.join(2000);
        assertFalse(bridgeTransport.isConnected());
    }
}
