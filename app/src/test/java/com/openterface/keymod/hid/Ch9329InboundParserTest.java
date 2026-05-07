package com.openterface.keymod.hid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ShadowLooper;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * Golden frame: GET_INFO ack {@code CMD=0x81}, {@code LEN=3}, {@code DATA[2]=0x07} (Num+Caps+Scroll),
 * valid CH9329 checksum.
 *
 * <pre>
 *   57 AB 00 81 03 30 01 07 BE
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Ch9329InboundParserTest {

    private static final byte[] GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON =
            hexToBytes("57AB008103300107BE");

    @Before
    public void setUp() {
        HostKeyboardLockLeds.get().reset();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @Test
    public void append_fullFrame_updatesHostLockLeds() {
        Ch9329InboundParser p = new Ch9329InboundParser();
        p.append(GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON, GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON.length);
        ShadowLooper.idleMainLooper();

        HostKeyboardLockLeds h = HostKeyboardLockLeds.get();
        assertTrue(h.hasReceivedLedFromHost());
        assertTrue(h.isNumLock());
        assertTrue(h.isCapsLock());
        assertTrue(h.isScrollLock());
    }

    @Test
    public void append_splitChunks_updatesHostLockLeds() {
        Ch9329InboundParser p = new Ch9329InboundParser();
        p.append(GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON, 4);
        byte[] rest =
                Arrays.copyOfRange(
                        GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON,
                        4,
                        GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON.length);
        p.append(rest, rest.length);
        ShadowLooper.idleMainLooper();

        HostKeyboardLockLeds h = HostKeyboardLockLeds.get();
        assertTrue(h.hasReceivedLedFromHost());
        assertTrue(h.isNumLock());
        assertTrue(h.isCapsLock());
        assertTrue(h.isScrollLock());
    }

    @Test
    public void append_badChecksum_doesNotApply() {
        byte[] bad = GOLDEN_GET_INFO_ACK_ALL_LOCKS_ON.clone();
        bad[bad.length - 1] = (byte) 0x00;

        Ch9329InboundParser p = new Ch9329InboundParser();
        p.append(bad, bad.length);
        ShadowLooper.idleMainLooper();

        assertFalse(HostKeyboardLockLeds.get().hasReceivedLedFromHost());
    }

    private static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
