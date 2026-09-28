package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for GoogleAdapter — covers URL building, role mapping,
 * system instruction extraction, and response parsing.
 */
@RunWith(RobolectricTestRunner.class)
public class GoogleAdapterTest {

    private GoogleAdapter adapter;

    @Before
    public void setUp() {
        adapter = new GoogleAdapter();
    }

    // ── URL building ───────────────────────────────────────────────────

    @Test
    public void testBuildUrlIncludesModel() {
        String url = adapter.buildUrl("https://generativelanguage.googleapis.com", "gemini-pro");
        assertTrue("URL should contain model path",
                url.contains("/v1beta/models/gemini-pro:generateContent"));
    }

    @Test
    public void testBuildUrlStripsTrailingSlash() {
        String url = adapter.buildUrl("https://generativelanguage.googleapis.com/", "gemini-pro");
        assertFalse("URL should not have double slashes",
                url.contains("googleapis.com//v1beta"));
        assertTrue(url.contains("/v1beta/models/gemini-pro:generateContent"));
    }

    // ── Auth URL ───────────────────────────────────────────────────────

    @Test
    public void testAppendAuthToUrl() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
        String authed = adapter.appendAuthToUrl(url, "my-api-key");
        assertTrue("URL should contain key param",
                authed.endsWith("?key=my-api-key"));
    }

    @Test
    public void testAppendAuthToUrlWithExistingParams() {
        String url = "https://example.com/api?alt=sse";
        String authed = adapter.appendAuthToUrl(url, "key123");
        assertTrue("URL should use & separator when ? exists",
                authed.contains("&key=key123"));
    }

    // ── Request body building ──────────────────────────────────────────

    @Test
    public void testBuildBodyRoles() throws JSONException {
        LlmRequest request = new LlmRequest("gemini-pro");
        request.addUserMessage("Hello");
        request.addAssistantMessage("Hi!");

        JSONObject body = adapter.buildBody(request, false);
        String bodyStr = body.toString();

        // "assistant" should be mapped to "model"
        assertTrue("Body should contain model role",
                bodyStr.contains("\"role\":\"model\""));
        assertFalse("Body should not contain assistant role",
                bodyStr.contains("\"role\":\"assistant\""));
        assertTrue("Body should contain user role",
                bodyStr.contains("\"role\":\"user\""));
    }

    @Test
    public void testBuildBodySystem() throws JSONException {
        LlmRequest request = new LlmRequest("gemini-pro");
        request.addSystemMessage("You are helpful.");
        request.addUserMessage("Hello");

        JSONObject body = adapter.buildBody(request, false);

        // system should be extracted to systemInstruction
        assertTrue("Body should contain systemInstruction",
                body.has("systemInstruction"));
        JSONObject sysInst = body.getJSONObject("systemInstruction");
        assertTrue("systemInstruction should contain text",
                sysInst.toString().contains("You are helpful."));

        // System message should NOT be in contents
        JSONArray contents = body.getJSONArray("contents");
        for (int i = 0; i < contents.length(); i++) {
            JSONObject content = contents.getJSONObject(i);
            assertNotEquals("No system role in contents",
                    "system", content.optString("role"));
        }
    }

    @Test
    public void testBuildBodyGenerationConfig() throws JSONException {
        LlmRequest request = new LlmRequest("gemini-pro");
        request.maxTokens = 2048;
        request.temperature = 0.5;
        request.addUserMessage("Hello");

        JSONObject body = adapter.buildBody(request, false);
        assertTrue("Body should contain generationConfig",
                body.has("generationConfig"));

        JSONObject config = body.getJSONObject("generationConfig");
        assertEquals(2048, config.getInt("maxOutputTokens"));
        assertEquals(0.5, config.getDouble("temperature"), 0.001);
    }

    // ── Response parsing ───────────────────────────────────────────────

    @Test
    public void testParseResponse() throws JSONException {
        String body = "{"
                + "\"candidates\":[{"
                + "\"content\":{\"parts\":[{\"text\":\"I'm doing well!\"}],\"role\":\"model\"},"
                + "\"finishReason\":\"STOP\""
                + "}],"
                + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5}"
                + "}";

        LlmResponse response = adapter.parseNonStreamingResponse(body);
        assertEquals("I'm doing well!", response.content);
        assertEquals("stop", response.finishReason);
        assertEquals(10, response.promptTokens);
        assertEquals(5, response.completionTokens);
    }

    @Test
    public void testParseResponseEmptyCandidates() throws JSONException {
        String body = "{\"candidates\":[],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":0}}";

        LlmResponse response = adapter.parseNonStreamingResponse(body);
        assertEquals("", response.content);
        assertNull(response.finishReason);
    }

    // ── SSE parser factory ─────────────────────────────────────────────

    @Test
    public void testCreateSseParser() {
        SseParser parser = adapter.createSseParser();
        assertNotNull(parser);
        assertTrue("Should return GeminiSseParser",
                parser instanceof GeminiSseParser);
    }

    // ── Provider metadata ──────────────────────────────────────────────

    @Test
    public void testUnsupportedMessage() {
        assertNull("Google adapter should be fully supported",
                adapter.unsupportedMessage());
    }

    @Test
    public void testSupportsStreaming() {
        assertTrue(adapter.supportsStreaming());
    }

    @Test
    public void testName() {
        assertEquals("Google", adapter.name());
    }
}
