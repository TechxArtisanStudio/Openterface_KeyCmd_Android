package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GamepadCapLabelsTest {

    @Test
    public void clampAsciiTruncatesByCodePoints() {
        assertEquals("123456", GamepadCapLabels.clampToMaxCodePoints("1234567", 6));
        assertEquals("", GamepadCapLabels.clampToMaxCodePoints("", 6));
    }

    @Test
    public void clampRespectsEmojiCodePoints() {
        String grin = "\uD83D\uDE00"; // U+1F600, one code point, two Java chars
        String fiveEmoji = grin + grin + grin + grin + grin;
        assertEquals(5, GamepadCapLabels.codePointCount(fiveEmoji));
        assertEquals(fiveEmoji, GamepadCapLabels.clampToMaxCodePoints(fiveEmoji, 6));
        String sixEmoji = fiveEmoji + grin;
        assertEquals(6, GamepadCapLabels.codePointCount(sixEmoji));
        String sevenEmoji = sixEmoji + grin;
        assertEquals(sixEmoji, GamepadCapLabels.clampToMaxCodePoints(sevenEmoji, 6));
    }
}
