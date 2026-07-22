package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Stub adapter for Anthropic — not yet implemented.
 *
 * Anthropic uses a different API format:
 *   - Endpoint: /v1/messages (not /chat/completions)
 *   - Auth header: x-api-key (not Authorization: Bearer)
 *   - Request body: { model, max_tokens, system, messages: [{role, content}] }
 *   - Response: { id, type, content: [{type, text}], usage }
 *
 * This stub returns a clear error message so users know support is coming.
 * Implement this class in Day 4.
 */
public final class AnthropicAdapter implements ProviderAdapter {

    @Override
    public String name() {
        return "Anthropic";
    }

    @Override
    public String buildUrl(String endpoint) {
        return endpoint + "/messages";
    }

    @Override
    public void setAuthHeaders(HttpURLConnection conn, String apiKey) {
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
    }

    @Override
    public JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException {
        throw new UnsupportedOperationException(
                "Anthropic adapter is not yet implemented. Coming in Day 4.");
    }

    @Override
    public LlmResponse parseNonStreamingResponse(String body) throws JSONException {
        throw new UnsupportedOperationException(
                "Anthropic adapter is not yet implemented. Coming in Day 4.");
    }

    @Override
    public boolean supportsStreaming() {
        return true; // Anthropic supports streaming, but not implemented yet
    }

    @Override
    public String unsupportedMessage() {
        return "Anthropic uses a non-OpenAI-compatible API format. " +
                "This will be supported in a future update.";
    }
}
