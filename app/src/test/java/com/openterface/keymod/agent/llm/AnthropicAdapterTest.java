package com.openterface.keymod.agent.llm;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnthropicAdapter}.
 */
@RunWith(RobolectricTestRunner.class)
public class AnthropicAdapterTest {

    private AnthropicAdapter adapter;

    @Before
    public void setUp() {
        adapter = new AnthropicAdapter();
    }

    // ── Provider metadata ──────────────────────────────────────────────

    @Test
    public void testName() {
        assertEquals("Anthropic", adapter.name());
    }

    @Test
    public void testSupportsStreaming() {
        assertTrue(adapter.supportsStreaming());
    }

    @Test
    public void testUnsupportedMessageIsNull() {
        assertNull(adapter.unsupportedMessage());
    }

    // ── URL building ───────────────────────────────────────────────────

    @Test
    public void testBuildUrlAppendsV1AndMessages() {
        String url = adapter.buildUrl("https://api.anthropic.com", "claude-3-sonnet");
        assertEquals("https://api.anthropic.com/v1/messages", url);
    }

    @Test
    public void testBuildUrlWithTrailingSlash() {
        String url = adapter.buildUrl("https://api.anthropic.com/", "claude-3-sonnet");
        assertEquals("https://api.anthropic.com/v1/messages", url);
    }

    @Test
    public void testBuildUrlAlreadyHasV1() {
        String url = adapter.buildUrl("https://api.anthropic.com/v1", "claude-3-sonnet");
        assertEquals("https://api.anthropic.com/v1/messages", url);
    }

    // ── Auth headers ───────────────────────────────────────────────────

    @Test
    public void testSetAuthHeaders() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.anthropic.com/v1/messages").openConnection();
        adapter.setAuthHeaders(conn, "sk-ant-test");
        assertEquals("sk-ant-test", conn.getRequestProperty("x-api-key"));
        assertEquals("2023-06-01", conn.getRequestProperty("anthropic-version"));
        assertTrue(conn.getRequestProperty("Content-Type").contains("application/json"));
    }

    @Test
    public void testSetAuthHeadersNullKey() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.anthropic.com/v1/messages").openConnection();
        adapter.setAuthHeaders(conn, null);
        assertNull(conn.getRequestProperty("x-api-key"));
        // anthropic-version should still be set
        assertEquals("2023-06-01", conn.getRequestProperty("anthropic-version"));
    }

    // ── Request body ───────────────────────────────────────────────────

    @Test
    public void testBuildBodySystemMessage() throws JSONException {
        LlmRequest request = new LlmRequest("claude-3-sonnet");
        request.addSystemMessage("You are helpful.");
        request.addUserMessage("Hello");
        request.maxTokens = 1024;

        JSONObject body = adapter.buildBody(request, false);
        assertEquals("claude-3-sonnet", body.getString("model"));
        assertEquals(1024, body.getInt("max_tokens"));
        assertFalse(body.getBoolean("stream"));

        // System should be top-level field
        assertEquals("You are helpful.", body.getString("system"));

        // Messages should not contain system role
        org.json.JSONArray msgs = body.getJSONArray("messages");
        assertEquals(1, msgs.length());
        assertEquals("user", msgs.getJSONObject(0).getString("role"));
    }

    @Test
    public void testBuildBodyUserAndAssistant() throws JSONException {
        LlmRequest request = new LlmRequest("claude-3-sonnet");
        request.addUserMessage("Hello");
        request.addAssistantMessage("Hi there!");
        request.addUserMessage("How are you?");

        JSONObject body = adapter.buildBody(request, false);
        org.json.JSONArray msgs = body.getJSONArray("messages");
        assertEquals(3, msgs.length());
        assertEquals("user", msgs.getJSONObject(0).getString("role"));
        assertEquals("assistant", msgs.getJSONObject(1).getString("role"));
        assertEquals("user", msgs.getJSONObject(2).getString("role"));
    }

    @Test
    public void testBuildBodyDefaultMaxTokens() throws JSONException {
        LlmRequest request = new LlmRequest("claude-3-sonnet");
        request.maxTokens = 0; // not set → should default to 4096
        request.addUserMessage("Hello");

        JSONObject body = adapter.buildBody(request, false);
        assertEquals(4096, body.getInt("max_tokens"));
    }

    @Test
    public void testBuildBodyTemperature() throws JSONException {
        LlmRequest request = new LlmRequest("claude-3-sonnet");
        request.temperature = 0.5;
        request.addUserMessage("Hello");

        JSONObject body = adapter.buildBody(request, false);
        assertEquals(0.5, body.getDouble("temperature"), 0.001);
    }

    // ── Response parsing ───────────────────────────────────────────────

    @Test
    public void testParseNonStreamingResponse() throws JSONException {
        String body = "{"
                + "\"id\":\"msg_01X\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-3-sonnet\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}"
                + "}";

        LlmResponse resp = adapter.parseNonStreamingResponse(body);
        assertEquals("Hello!", resp.content);
        assertEquals("end_turn", resp.finishReason);
        assertEquals("claude-3-sonnet", resp.model);
        assertEquals(10, resp.promptTokens);
        assertEquals(5, resp.completionTokens);
    }

    @Test
    public void testParseNonStreamingResponseMultipleBlocks() throws JSONException {
        String body = "{"
                + "\"content\":["
                + "{\"type\":\"text\",\"text\":\"First paragraph\"},"
                + "{\"type\":\"text\",\"text\":\"Second paragraph\"}"
                + "],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":5,\"output_tokens\":10}"
                + "}";

        LlmResponse resp = adapter.parseNonStreamingResponse(body);
        assertTrue(resp.content.contains("First paragraph"));
        assertTrue(resp.content.contains("Second paragraph"));
        assertTrue(resp.content.contains("\n"));
    }

    // ── SSE parser factory ─────────────────────────────────────────────

    @Test
    public void testCreateSseParser() {
        SseParser parser = adapter.createSseParser();
        assertNotNull(parser);
        assertTrue(parser instanceof AnthropicSseParser);
    }
}
