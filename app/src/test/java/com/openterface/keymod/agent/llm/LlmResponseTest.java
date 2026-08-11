package com.openterface.keymod.agent.llm;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link LlmResponse}.
 */
public class LlmResponseTest {

    // ── Constructor ────────────────────────────────────────────────────

    @Test
    public void testConstructorAllFields() {
        LlmResponse resp = new LlmResponse("hello", "stop", 10, 5, "gpt-4", null);
        assertEquals("hello", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals(10, resp.promptTokens);
        assertEquals(5, resp.completionTokens);
        assertEquals("gpt-4", resp.model);
        assertNull(resp.toolCalls);
    }

    @Test
    public void testConstructorNullContentBecomesEmpty() {
        LlmResponse resp = new LlmResponse(null, "stop", 0, 0);
        assertEquals("", resp.content);
    }

    @Test
    public void testConstructorFourArgs() {
        LlmResponse resp = new LlmResponse("text", "stop", 10, 5);
        assertEquals("text", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals(10, resp.promptTokens);
        assertEquals(5, resp.completionTokens);
        assertNull(resp.model);
        assertNull(resp.toolCalls);
    }

    // ── delta() factory ────────────────────────────────────────────────

    @Test
    public void testDeltaFactory() {
        LlmResponse resp = LlmResponse.delta("Hello world");
        assertEquals("Hello world", resp.content);
        assertNull(resp.finishReason);
        assertEquals(0, resp.promptTokens);
        assertEquals(0, resp.completionTokens);
        assertNull(resp.model);
        assertNull(resp.toolCalls);
    }

    // ── terminal() factories ───────────────────────────────────────────

    @Test
    public void testTerminalFactoryWithReason() {
        LlmResponse resp = LlmResponse.terminal("stop");
        assertEquals("", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals(0, resp.promptTokens);
        assertEquals(0, resp.completionTokens);
    }

    @Test
    public void testTerminalFactoryWithTokens() {
        LlmResponse resp = LlmResponse.terminal("stop", 100, 50);
        assertEquals("", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals(100, resp.promptTokens);
        assertEquals(50, resp.completionTokens);
    }

    @Test
    public void testTerminalFactoryWithContentAndTokens() {
        LlmResponse resp = LlmResponse.terminal("final text", "stop", 100, 50);
        assertEquals("final text", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals(100, resp.promptTokens);
        assertEquals(50, resp.completionTokens);
    }

    // ── withToolCalls() factory ────────────────────────────────────────

    @Test
    public void testWithToolCallsFactory() {
        List<LlmRequest.ToolCall> calls = Arrays.asList(
                new LlmRequest.ToolCall("call_1", "function",
                        new LlmRequest.FunctionCall("get_weather", "{\"city\":\"Beijing\"}")));
        LlmResponse resp = LlmResponse.withToolCalls(calls, "stop");
        assertEquals("", resp.content);
        assertEquals("stop", resp.finishReason);
        assertTrue(resp.hasToolCalls());
        assertEquals(1, resp.toolCalls.size());
        assertEquals("get_weather", resp.toolCalls.get(0).function.name);
    }

    // ── isTerminal() ───────────────────────────────────────────────────

    @Test
    public void testIsTerminalTrue() {
        assertTrue(LlmResponse.terminal("stop").isTerminal());
        assertTrue(LlmResponse.terminal("length").isTerminal());
        assertTrue(new LlmResponse("text", "stop", 0, 0).isTerminal());
    }

    @Test
    public void testIsTerminalFalse() {
        assertFalse(LlmResponse.delta("text").isTerminal());
        assertFalse(new LlmResponse("text", null, 0, 0).isTerminal());
    }

    // ── hasToolCalls() ─────────────────────────────────────────────────

    @Test
    public void testHasToolCallsFalseWhenNull() {
        LlmResponse resp = new LlmResponse("text", "stop", 0, 0, null, null);
        assertFalse(resp.hasToolCalls());
    }

    @Test
    public void testHasToolCallsFalseWhenEmpty() {
        LlmResponse resp = new LlmResponse("text", "stop", 0, 0, null,
                Collections.emptyList());
        assertFalse(resp.hasToolCalls());
    }

    @Test
    public void testHasToolCallsTrueWhenNonEmpty() {
        List<LlmRequest.ToolCall> calls = Arrays.asList(
                new LlmRequest.ToolCall("id", "function",
                        new LlmRequest.FunctionCall("fn", "{}")));
        LlmResponse resp = new LlmResponse("", "stop", 0, 0, null, calls);
        assertTrue(resp.hasToolCalls());
    }
}
