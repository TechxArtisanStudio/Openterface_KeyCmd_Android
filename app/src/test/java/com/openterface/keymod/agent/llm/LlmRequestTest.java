package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for LlmRequest data model.
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
        assertEquals("You are helpful", msgs.getJSONObject(0).getString("content"));
        assertEquals("user", msgs.getJSONObject(1).getString("role"));
        assertEquals("Hi", msgs.getJSONObject(1).getString("content"));
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"));
        assertEquals("Hello!", msgs.getJSONObject(2).getString("content"));
        assertEquals("user", msgs.getJSONObject(3).getString("role"));
        assertEquals("How are you?", msgs.getJSONObject(3).getString("content"));
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
        req.stream = true;
        JSONObject json = req.toJson();
        assertTrue(json.getBoolean("stream"));
    }
}
