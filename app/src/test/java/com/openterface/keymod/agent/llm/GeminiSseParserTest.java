package com.openterface.keymod.agent.llm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GeminiSseParser}.
 */
@RunWith(RobolectricTestRunner.class)
public class GeminiSseParserTest {

    private GeminiSseParser parser;

    @Before
    public void setUp() {
        parser = new GeminiSseParser();
    }

    // ── parseLine: basic delta ─────────────────────────────────────────

    @Test
    public void testParseFirstDelta() {
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}],\"role\":\"model\"}}]}");
        assertNotNull(resp);
        assertEquals("Hello", resp.content);
        assertNull(resp.finishReason);
        assertFalse(resp.isTerminal());
    }

    @Test
    public void testParseSubsequentDeltaComputesDifference() {
        // First chunk: accumulated = "Hello"
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}");

        // Second chunk: accumulated = "Hello world" → delta = " world"
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello world\"}]}}]}");
        assertNotNull(resp);
        assertEquals(" world", resp.content);
    }

    @Test
    public void testParseThirdChunkContinuesDelta() {
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"A\"}]}}]}");
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"AB\"}]}}]}");
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ABC\"}]}}]}");
        assertNotNull(resp);
        assertEquals("C", resp.content);
    }

    // ── parseLine: terminal chunk ──────────────────────────────────────

    @Test
    public void testParseTerminalStop() {
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Done\"}]},\"finishReason\":\"STOP\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5}}");
        assertNotNull(resp);
        assertTrue(resp.isTerminal());
        assertEquals("stop", resp.finishReason);
        assertEquals(10, resp.promptTokens);
        assertEquals(5, resp.completionTokens);
    }

    @Test
    public void testParseTerminalMaxTokens() {
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]},\"finishReason\":\"MAX_TOKENS\"}]}");
        assertNotNull(resp);
        assertTrue(resp.isTerminal());
        assertEquals("length", resp.finishReason);
    }

    @Test
    public void testParseTerminalSafety() {
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]},\"finishReason\":\"SAFETY\"}]}");
        assertNotNull(resp);
        assertTrue(resp.isTerminal());
        assertEquals("safety", resp.finishReason);
    }

    // ── parseLine: edge cases ──────────────────────────────────────────

    @Test
    public void testParseNullLine() {
        assertNull(parser.parseLine(null));
    }

    @Test
    public void testParseEmptyLine() {
        assertNull(parser.parseLine(""));
    }

    @Test
    public void testParseNonDataLine() {
        assertNull(parser.parseLine("event: message"));
    }

    @Test
    public void testParseMalformedJson() {
        assertNull(parser.parseLine("data: {bad json!!!"));
    }

    @Test
    public void testParseEmptyPayload() {
        assertNull(parser.parseLine("data: "));
    }

    @Test
    public void testParseEmptyCandidates() {
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[]}");
        // Empty candidates → no text, no finish → null
        assertNull(resp);
    }

    // ── Token tracking ─────────────────────────────────────────────────

    @Test
    public void testTokenCountsUpdateOnUsageChunk() {
        // Token counts are only saved on terminal events (with finishReason).
        // This matches Gemini's real behavior: usageMetadata arrives with the final chunk.
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]},\"finishReason\":\"STOP\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":7}}");
        assertEquals(42, parser.getPromptTokens());
        assertEquals(7, parser.getCompletionTokens());
    }

    @Test
    public void testTokenCountsInitiallyZero() {
        assertEquals(0, parser.getPromptTokens());
        assertEquals(0, parser.getCompletionTokens());
    }

    // ── reset() ────────────────────────────────────────────────────────

    @Test
    public void testResetClearsState() {
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}");
        parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello world\"}]}}," +
                        "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5}]}");

        parser.reset();
        assertEquals(0, parser.getPromptTokens());
        assertEquals(0, parser.getCompletionTokens());

        // After reset, first delta should be full text (not computed against previous)
        LlmResponse resp = parser.parseLine(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Fresh\"}]}}]}");
        assertNotNull(resp);
        assertEquals("Fresh", resp.content);
    }

    // ── mapFinishReason ────────────────────────────────────────────────

    @Test
    public void testMapFinishReasonStop() {
        assertEquals("stop", GeminiSseParser.mapFinishReason("STOP"));
    }

    @Test
    public void testMapFinishReasonMaxTokens() {
        assertEquals("length", GeminiSseParser.mapFinishReason("MAX_TOKENS"));
    }

    @Test
    public void testMapFinishReasonSafety() {
        assertEquals("safety", GeminiSseParser.mapFinishReason("SAFETY"));
    }

    @Test
    public void testMapFinishReasonRecitation() {
        assertEquals("recitation", GeminiSseParser.mapFinishReason("RECITATION"));
    }

    @Test
    public void testMapFinishReasonNull() {
        assertNull(GeminiSseParser.mapFinishReason(null));
    }

    @Test
    public void testMapFinishReasonEmpty() {
        assertNull(GeminiSseParser.mapFinishReason(""));
    }

    @Test
    public void testMapFinishReasonUnknown() {
        assertEquals("other", GeminiSseParser.mapFinishReason("OTHER"));
    }
}
