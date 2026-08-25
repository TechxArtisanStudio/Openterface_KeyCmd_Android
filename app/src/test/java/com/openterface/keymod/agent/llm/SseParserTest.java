package com.openterface.keymod.agent.llm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for SseParser — covers text deltas, [DONE], blank/comment lines,
 * malformed JSON, terminal chunks, and streaming tool call assembly.
 */
@RunWith(RobolectricTestRunner.class)
public class SseParserTest {

    private SseParser parser;

    @Before
    public void setUp() {
        parser = new SseParser();
    }

    @Test
    public void testParseNormalDelta() {
        LlmResponse resp = parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}");
        assertNotNull(resp);
        assertEquals("Hello", resp.content);
        assertNull(resp.finishReason);
        assertFalse(resp.isTerminal());
    }

    @Test(expected = SseParser.StreamDoneException.class)
    public void testParseDoneMarker() {
        parser.parseLine("data: [DONE]");
    }

    @Test
    public void testParseBlankLine() {
        assertNull(parser.parseLine(""));
        assertNull(parser.parseLine(null));
    }

    @Test
    public void testParseCommentLine() {
        assertNull(parser.parseLine(": keepalive"));
    }

    @Test
    public void testParseNonDataLine() {
        assertNull(parser.parseLine("event: message"));
        assertNull(parser.parseLine("id: 123"));
        assertNull(parser.parseLine("retry: 5000"));
    }

    @Test
    public void testParseTerminalChunk() {
        LlmResponse resp = parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"done\"},\"finish_reason\":\"stop\"}]}");
        assertNotNull(resp);
        assertEquals("done", resp.content);
        assertEquals("stop", resp.finishReason);
        assertTrue(resp.isTerminal());
    }

    @Test
    public void testParseMalformedJson() {
        assertNull("Malformed JSON should be skipped",
                parser.parseLine("data: {bad json!!!"));
    }

    @Test
    public void testParseEmptyContent() {
        LlmResponse resp = parser.parseLine(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}");
        assertNotNull(resp);
        assertEquals("", resp.content);
        assertNull(resp.finishReason);
    }

    @Test
    public void testParseNoDelta() {
        LlmResponse resp = parser.parseLine(
                "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}");
        assertNotNull(resp);
        assertEquals("", resp.content);
        assertEquals("stop", resp.finishReason);
        assertTrue(resp.isTerminal());
    }

    // ── Streaming tool call tests ────────────────────────────────────

    @Test
    public void testStreamToolCallAssembly() {
        // Simulate OpenAI's incremental tool call streaming:
        // Chunk 1: id + function name
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"}}]}}]}");
        // Chunk 2: first arguments fragment
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"city\\\":\"}}]}}]}");
        // Chunk 3: second arguments fragment
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"Beijing\\\"}\"}}]}}]}");
        // Terminal
        parser.parseLine("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        java.util.List<LlmRequest.ToolCall> toolCalls = parser.collectToolCalls();
        assertEquals(1, toolCalls.size());

        LlmRequest.ToolCall tc = toolCalls.get(0);
        assertEquals("call_abc", tc.id);
        assertEquals("function", tc.type);
        assertEquals("get_weather", tc.function.name);
        assertEquals("{\"city\":\"Beijing\"}", tc.function.arguments);
    }

    @Test
    public void testStreamToolCallWithTextContent() {
        // Some models emit text + tool_calls in the same stream
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"Let me check\"}}]}");
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"search\"}}]}}]}");
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{}\"}}]}}]}");
        parser.parseLine("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        java.util.List<LlmRequest.ToolCall> toolCalls = parser.collectToolCalls();
        assertEquals(1, toolCalls.size());
        assertEquals("search", toolCalls.get(0).function.name);
    }

    @Test
    public void testReset() {
        parser.parseLine("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"test\"}}]}}]}");
        assertEquals(1, parser.collectToolCalls().size());

        parser.reset();
        assertEquals(0, parser.collectToolCalls().size());
    }
}
