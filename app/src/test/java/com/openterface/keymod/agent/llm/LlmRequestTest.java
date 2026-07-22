package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for LlmRequest data model — covers JSON structure,
 * fluent builder, tool calls serialization, and default values.
 */
@RunWith(RobolectricTestRunner.class)
public class LlmRequestTest {

    @Test
    public void testToJsonStructure() throws Exception {
        LlmRequest req = new LlmRequest("gpt-4o-mini");
        req.addUserMessage("Hello");
        JSONObject json = req.toJson();

        assertEquals("gpt-4o-mini", json.getString("model"));
        assertTrue(json.has("max_tokens"));
        assertTrue(json.has("temperature"));
        assertTrue(json.has("stream"));
        assertTrue(json.has("messages"));

        JSONArray msgs = json.getJSONArray("messages");
        assertEquals(1, msgs.length());
        JSONObject msg = msgs.getJSONObject(0);
        assertEquals("user", msg.getString("role"));
        assertEquals("Hello", msg.getString("content"));
    }

    @Test
    public void testMultipleMessages() throws Exception {
        LlmRequest req = new LlmRequest("gpt-4");
        req.addSystemMessage("You are helpful")
           .addUserMessage("Hi")
           .addAssistantMessage("Hello!")
           .addUserMessage("How are you?");

        JSONObject json = req.toJson();
        JSONArray msgs = json.getJSONArray("messages");
        assertEquals(4, msgs.length());

        assertEquals("system", msgs.getJSONObject(0).getString("role"));
        assertEquals("user", msgs.getJSONObject(1).getString("role"));
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"));
        assertEquals("user", msgs.getJSONObject(3).getString("role"));
    }

    @Test
    public void testDefaultValues() throws Exception {
        LlmRequest req = new LlmRequest("test-model");
        assertEquals("test-model", req.model);
        assertEquals(2048, req.maxTokens);
        assertFalse(req.stream);
        assertEquals(0.7, req.temperature, 0.001);
        assertTrue(req.messages.isEmpty());
    }

    @Test
    public void testStreamFlag() throws Exception {
        LlmRequest req = new LlmRequest("gpt-4o-mini");
        JSONObject json = req.toJson(true);
        assertTrue(json.getBoolean("stream"));
    }

    // ── Tool call tests ──────────────────────────────────────────────

    @Test
    public void testToolCallSerialization() throws Exception {
        LlmRequest req = new LlmRequest("gpt-4o");

        // Assistant message with tool call
        LlmRequest.FunctionCall fn = new LlmRequest.FunctionCall(
                "get_weather", "{\"city\": \"Beijing\"}");
        LlmRequest.ToolCall tc = new LlmRequest.ToolCall("call_abc", "function", fn);
        req.addAssistantToolCalls("", Arrays.asList(tc));

        // Tool result
        req.addToolResult("call_abc", "{\"temp\": 25}");

        JSONObject json = req.toJson();
        JSONArray msgs = json.getJSONArray("messages");
        assertEquals(2, msgs.length());

        // Assistant message with tool_calls
        JSONObject assistantMsg = msgs.getJSONObject(0);
        assertEquals("assistant", assistantMsg.getString("role"));
        JSONArray toolCalls = assistantMsg.getJSONArray("tool_calls");
        assertEquals(1, toolCalls.length());
        assertEquals("call_abc", toolCalls.getJSONObject(0).getString("id"));
        assertEquals("function", toolCalls.getJSONObject(0).getString("type"));
        assertEquals("get_weather",
                toolCalls.getJSONObject(0).getJSONObject("function").getString("name"));

        // Tool result message
        JSONObject toolMsg = msgs.getJSONObject(1);
        assertEquals("tool", toolMsg.getString("role"));
        assertEquals("{\"temp\": 25}", toolMsg.getString("content"));
        assertEquals("call_abc", toolMsg.getString("tool_call_id"));
    }
}
