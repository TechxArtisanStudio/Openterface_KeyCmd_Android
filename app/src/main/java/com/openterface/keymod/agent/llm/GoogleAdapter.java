package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Stub adapter for Google Gemini — not yet implemented.
 *
 * Gemini uses a completely different REST structure:
 *   - Endpoint: /v1beta/models/{model}:generateContent
 *   - Auth: API key as query parameter (?key=...)
 *   - Request body: { contents: [{role, parts: [{text}]}], generationConfig }
 *   - Response: { candidates: [{content: {parts: [{text}]}, finishReason}], usageMetadata }
 *
 * This stub returns a clear error message so users know support is coming.
 * Implement this class in Day 4.
 */
public final class GoogleAdapter implements ProviderAdapter {

    @Override
    public String name() {
        return "Google";
    }

    @Override
    public String buildUrl(String endpoint) {
        // Gemini uses a different URL pattern: /models/{model}:generateContent
        // The actual URL will be constructed with the model name
        return endpoint;
    }

    @Override
    public void setAuthHeaders(HttpURLConnection conn, String apiKey) {
        conn.setRequestProperty("Content-Type", "application/json");
        // Gemini uses API key as query parameter, not header
    }

    @Override
    public JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException {
        throw new UnsupportedOperationException(
                "Google Gemini adapter is not yet implemented. Coming in Day 4.");
    }

    @Override
    public LlmResponse parseNonStreamingResponse(String body) throws JSONException {
        throw new UnsupportedOperationException(
                "Google Gemini adapter is not yet implemented. Coming in Day 4.");
    }

    @Override
    public boolean supportsStreaming() {
        return true; // Gemini supports streaming, but not implemented yet
    }

    @Override
    public String unsupportedMessage() {
        return "Google Gemini uses a non-OpenAI-compatible API format. " +
                "This will be supported in a future update.";
    }
}
