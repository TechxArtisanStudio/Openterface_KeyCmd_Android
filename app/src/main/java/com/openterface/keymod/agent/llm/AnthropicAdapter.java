package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ProviderAdapter for Anthropic's Messages API.
 *
 * <p>API format:
 * <ul>
 *   <li>Endpoint: {@code /v1/messages}</li>
 *   <li>Auth: {@code x-api-key} header + {@code anthropic-version} header</li>
 *   <li>Request: {@code { model, max_tokens, system?, messages: [{role, content}] }}</li>
 *   <li>Response: {@code { id, type, content: [{type, text}], stop_reason, usage }}</li>
 * </ul>
 *
 * <p>Important differences from OpenAI:
 * <ul>
 *   <li>{@code system} is a top-level string field, not a message role</li>
 *   <li>Messages can only have {@code user} or {@code assistant} roles</li>
 *   <li>{@code max_tokens} is required (default 4096)</li>
 *   <li>Content is an array of blocks, not a simple string</li>
 * </ul>
 */
public final class AnthropicAdapter implements ProviderAdapter {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Override
    public String name() {
        return "Anthropic";
    }

    @Override
    public String buildUrl(String endpoint, String model) {
        // Ensure endpoint ends with /v1
        String base = endpoint;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1")) {
            base = base + "/v1";
        }
        return base + "/messages";
    }

    @Override
    public void setAuthHeaders(HttpURLConnection conn, String apiKey) {
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("x-api-key", apiKey);
        }
        conn.setRequestProperty("anthropic-version", "2023-06-01");
    }

    @Override
    public JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", request.model);
        body.put("max_tokens", request.maxTokens > 0 ? request.maxTokens : DEFAULT_MAX_TOKENS);
        body.put("stream", stream);

        if (request.temperature > 0) {
            body.put("temperature", request.temperature);
        }

        // Separate system message from regular messages
        JSONArray messages = new JSONArray();
        for (LlmRequest.Message msg : request.messages) {
            if ("system".equals(msg.role)) {
                // Anthropic uses top-level "system" field
                body.put("system", msg.content);
            } else if ("user".equals(msg.role) || "assistant".equals(msg.role)) {
                JSONObject m = new JSONObject();
                m.put("role", msg.role);
                m.put("content", msg.content);
                messages.put(m);
            }
            // Skip "tool" and other roles for now
        }
        body.put("messages", messages);
        return body;
    }

    @Override
    public LlmResponse parseNonStreamingResponse(String body) throws JSONException {
        JSONObject json = new JSONObject(body);

        // Extract text from content blocks
        JSONArray content = json.getJSONArray("content");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                if (text.length() > 0) text.append('\n');
                text.append(block.getString("text"));
            }
        }

        // Extract token usage
        JSONObject usage = json.optJSONObject("usage");
        int promptTokens = 0;
        int completionTokens = 0;
        if (usage != null) {
            promptTokens = usage.optInt("input_tokens", 0);
            completionTokens = usage.optInt("output_tokens", 0);
        }

        // Extract model and stop reason
        String model = json.optString("model", null);
        String stopReason = json.optString("stop_reason", "end_turn");

        return new LlmResponse(text.toString(), stopReason,
                promptTokens, completionTokens, model, null);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public SseParser createSseParser() {
        return new AnthropicSseParser();
    }

    @Override
    public String unsupportedMessage() {
        return null; // Fully supported
    }
}
