package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ProviderAdapter for Google Gemini API.
 *
 * <p>API format:
 * <ul>
 *   <li>Non-streaming URL: {@code .../v1beta/models/{model}:generateContent}</li>
 *   <li>Streaming URL: {@code .../v1beta/models/{model}:streamGenerateContent?alt=sse}</li>
 *   <li>Auth: API key as query parameter {@code ?key=API_KEY}</li>
 *   <li>Request: {@code { contents, systemInstruction?, generationConfig } }</li>
 *   <li>Response: {@code { candidates: [{content, finishReason}], usageMetadata } }</li>
 * </ul>
 *
 * <p>Important differences from OpenAI:
 * <ul>
 *   <li>Model name is embedded in the URL path</li>
 *   <li>API key goes in URL query param, not header</li>
 *   <li>Roles are {@code user} / {@code model} (not {@code assistant})</li>
 *   <li>System prompt uses {@code systemInstruction} field</li>
 *   <li>Content uses {@code parts} array with {@code text} field</li>
 * </ul>
 */
public final class GoogleAdapter implements ProviderAdapter {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Override
    public String name() {
        return "Google";
    }

    @Override
    public String buildUrl(String endpoint, String model) {
        // endpoint is the base URL (e.g. "https://generativelanguage.googleapis.com")
        // Model is embedded in the URL path
        String base = endpoint;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // If endpoint doesn't look like the Gemini base, use it as-is
        return base + "/v1beta/models/" + model + ":generateContent";
    }

    @Override
    public void setAuthHeaders(HttpURLConnection conn, String apiKey) {
        conn.setRequestProperty("Content-Type", "application/json");
        // Gemini uses API key as query parameter — handled by appendAuthToUrl
    }

    @Override
    public String appendAuthToUrl(String url, String apiKey) {
        // Gemini appends API key as query parameter
        if (apiKey != null && !apiKey.isEmpty()) {
            String separator = url.contains("?") ? "&" : "?";
            return url + separator + "key=" + apiKey;
        }
        return url;
    }

    @Override
    public JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException {
        JSONObject body = new JSONObject();

        // Build contents array (role mapping: assistant → model)
        JSONArray contents = new JSONArray();
        String systemText = null;

        for (LlmRequest.Message msg : request.messages) {
            if ("system".equals(msg.role)) {
                systemText = msg.content;
                continue;
            }

            String role = mapRole(msg.role);
            JSONObject contentObj = new JSONObject();
            contentObj.put("role", role);

            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", msg.content);
            parts.put(textPart);

            contentObj.put("parts", parts);
            contents.put(contentObj);
        }

        body.put("contents", contents);

        // System instruction (separate from contents)
        if (systemText != null && !systemText.isEmpty()) {
            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysTextPart = new JSONObject();
            sysTextPart.put("text", systemText);
            sysParts.put(sysTextPart);
            systemInstruction.put("parts", sysParts);
            body.put("systemInstruction", systemInstruction);
        }

        // Generation config
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("maxOutputTokens",
                request.maxTokens > 0 ? request.maxTokens : DEFAULT_MAX_TOKENS);
        if (request.temperature > 0) {
            generationConfig.put("temperature", request.temperature);
        }
        body.put("generationConfig", generationConfig);

        return body;
    }

    @Override
    public LlmResponse parseNonStreamingResponse(String body) throws JSONException {
        JSONObject json = new JSONObject(body);

        // Extract text from candidates[0].content.parts[0].text
        String text = "";
        String finishReason = null;

        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);

            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray parts = content.optJSONArray("parts");
                if (parts != null && parts.length() > 0) {
                    text = parts.getJSONObject(0).optString("text", "");
                }
            }

            finishReason = candidate.optString("finishReason", null);
        }

        // Extract token usage
        JSONObject usageMetadata = json.optJSONObject("usageMetadata");
        int promptTokens = 0;
        int completionTokens = 0;
        if (usageMetadata != null) {
            promptTokens = usageMetadata.optInt("promptTokenCount", 0);
            completionTokens = usageMetadata.optInt("candidatesTokenCount", 0);
        }

        // Map finish reason (shared with GeminiSseParser)
        String mappedFinish = GeminiSseParser.mapFinishReason(finishReason);

        return new LlmResponse(text, mappedFinish,
                promptTokens, completionTokens, null, null);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public SseParser createSseParser() {
        return new GeminiSseParser();
    }

    @Override
    public String unsupportedMessage() {
        return null; // Fully supported
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Map standard roles to Gemini roles.
     * OpenAI uses "assistant", Gemini uses "model".
     */
    private static String mapRole(String role) {
        if ("assistant".equals(role)) {
            return "model";
        }
        return role; // "user" stays "user"
    }
}
