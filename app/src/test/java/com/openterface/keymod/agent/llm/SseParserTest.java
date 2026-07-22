package com.openterface.keymod.agent.llm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for SseParser.
 */
@RunWith(RobolectricTestRunner.class)
public class SseParserTest {

    @Test
    public void testParseNormalDelta() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";
        LlmResponse resp = SseParser.parseLine(line);
        assertNotNull(resp);
        assertEquals("Hello", resp.content);
        assertNull(resp.finishReason);
        assertFalse(resp.isTerminal());
    }

    @Test(expected = SseParser.StreamDoneException.class)
    public void testParseDoneMarker() {
        String line = "data: [DONE]";
        SseParser.parseLine(line);
    }

    @Test
    public void testParseBlankLine() {
        assertNull(SseParser.parseLine(""));
        assertNull(SseParser.parseLine(null));
    }

    @Test
    public void testParseCommentLine() {
        // SSE comment (keep-alive ping)
        assertNull(SseParser.parseLine(": keepalive"));
    }

    @Test
    public void testParseNonDataLine() {
        assertNull(SseParser.parseLine("event: message"));
        assertNull(SseParser.parseLine("id: 123"));
        assertNull(SseParser.parseLine("retry: 5000"));
    }

    @Test
    public void testParseTerminalChunk() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"done\"},\"finish_reason\":\"stop\"}]}";
        LlmResponse resp = SseParser.parseLine(line);
        assertNotNull(resp);
        assertEquals("done", resp.content);
        assertEquals("stop", resp.finishReason);
        assertTrue(resp.isTerminal());
    }

    @Test
    public void testParseMalformedJson() {
        String line = "data: {bad json!!!";
        LlmResponse resp = SseParser.parseLine(line);
        assertNull("Malformed JSON should be skipped", resp);
    }

    @Test
    public void testParseEmptyContent() {
        // delta without content field
        String line = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}";
        LlmResponse resp = SseParser.parseLine(line);
        assertNotNull(resp);
        assertEquals("", resp.content);
        assertNull(resp.finishReason);
    }

    @Test
    public void testParseNoDelta() {
        // Some providers send terminal event without delta, only finish_reason
        String line = "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}";
        LlmResponse resp = SseParser.parseLine(line);
        assertNotNull(resp);
        assertEquals("", resp.content);
        assertEquals("stop", resp.finishReason);
        assertTrue(resp.isTerminal());
    }
}
