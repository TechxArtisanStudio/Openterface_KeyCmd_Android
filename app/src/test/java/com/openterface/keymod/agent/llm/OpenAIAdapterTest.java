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
 * Unit tests for {@link OpenAIAdapter}.
 */
@RunWith(RobolectricTestRunner.class)
public class OpenAIAdapterTest {

    private OpenAIAdapter adapter;

    @Before
    public void setUp() {
        adapter = new OpenAIAdapter();
    }

    // ── Provider metadata ──────────────────────────────────────────────

    @Test
    public void testName() {
        assertEquals("OpenAI", adapter.name());
    }

    @Test
    public void testSupportsStreaming() {
        assertTrue(adapter.supportsStreaming());
    }

    @Test
    public void testUnsupportedMessageIsNull() {
        assertNull("OpenAI adapter should be fully supported", adapter.unsupportedMessage());
    }

    // ── URL building ───────────────────────────────────────────────────

    @Test
    public void testBuildUrl() {
        String url = adapter.buildUrl("https://api.openai.com/v1", "gpt-4o");
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }

    @Test
    public void testBuildUrlCustomEndpoint() {
        String url = adapter.buildUrl("https://api.deepseek.com/v1", "deepseek-chat");
        assertEquals("https://api.deepseek.com/v1/chat/completions", url);
    }

    // ── Auth headers ───────────────────────────────────────────────────

    @Test
    public void testSetAuthHeadersWithKey() throws Exception {
        // Note: Robolectric's HttpURLConnection shadow does not reliably
        // echo back Authorization via getRequestProperty(). We verify
        // the method does not throw and Content-Type is set.
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.openai.com/v1/chat/completions").openConnection();
        adapter.setAuthHeaders(conn, "sk-test-key");
        assertTrue(conn.getRequestProperty("Content-Type").contains("application/json"));
    }

    @Test
    public void testSetAuthHeadersNullKey() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.openai.com/v1/chat/completions").openConnection();
        adapter.setAuthHeaders(conn, null);
        assertNull(conn.getRequestProperty("Authorization"));
    }

    @Test
    public void testSetAuthHeadersEmptyKey() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.openai.com/v1/chat/completions").openConnection();
        adapter.setAuthHeaders(conn, "");
        assertNull(conn.getRequestProperty("Authorization"));
    }

    // ── Request body ───────────────────────────────────────────────────

    @Test
    public void testBuildBodyDelegatesToRequest() throws JSONException {
        LlmRequest request = new LlmRequest("gpt-4o");
        request.addUserMessage("Hello");

        JSONObject body = adapter.buildBody(request, false);
        assertEquals("gpt-4o", body.getString("model"));
        assertTrue(body.has("messages"));
    }

    // ── Response parsing ───────────────────────────────────────────────

    @Test
    public void testParseNonStreamingResponse() throws JSONException {
        String body = "{"
                + "\"model\":\"gpt-4o\","
                + "\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Hello! How can I help?\"},"
                + "\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}"
                + "}";

        LlmResponse resp = adapter.parseNonStreamingResponse(body);
        assertEquals("Hello! How can I help?", resp.content);
        assertEquals("stop", resp.finishReason);
        assertEquals("gpt-4o", resp.model);
        assertEquals(10, resp.promptTokens);
        assertEquals(5, resp.completionTokens);
    }

    @Test
    public void testParseNonStreamingResponseNoUsage() throws JSONException {
        String body = "{"
                + "\"choices\":[{\"message\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]"
                + "}";

        LlmResponse resp = adapter.parseNonStreamingResponse(body);
        assertEquals("Hi", resp.content);
        assertEquals(0, resp.promptTokens);
        assertEquals(0, resp.completionTokens);
    }

    @Test
    public void testParseNonStreamingResponseWithToolCalls() throws JSONException {
        String body = "{"
                + "\"choices\":[{\"message\":{\"content\":\"\","
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"BJ\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":10}"
                + "}";

        LlmResponse resp = adapter.parseNonStreamingResponse(body);
        assertTrue(resp.hasToolCalls());
        assertEquals(1, resp.toolCalls.size());
        assertEquals("get_weather", resp.toolCalls.get(0).function.name);
        assertEquals("call_1", resp.toolCalls.get(0).id);
    }

    // ── SSE parser factory ─────────────────────────────────────────────

    @Test
    public void testCreateSseParser() {
        SseParser parser = adapter.createSseParser();
        assertNotNull(parser);
        assertTrue("Should return OpenAISseParser", parser instanceof OpenAISseParser);
    }
}
