package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ProviderAdapter for OpenAI-compatible APIs.
 *
 * Covers: OpenAI, Mistral, Groq, DashScope, DeepSeek, Ollama,
 * and any other provider that implements the OpenAI /chat/completions format.
 */
public final class OpenAIAdapter implements ProviderAdapter {

    @Override
    public String name() {
        return "OpenAI";
    }

    @Override
    public String buildUrl(String endpoint, String model) {
        return endpoint + "/chat/completions";
    }

    @Override
    public void setAuthHeaders(HttpURLConnection conn, String apiKey) {
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
    }

    @Override
    public JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException {
        return request.toJson(stream);
    }

    @Override
    public LlmResponse parseNonStreamingResponse(String body) throws JSONException {
        JSONObject json = new JSONObject(body);

        // Extract choice
        JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String content = message.getString("content");

        // Finish reason
        String finishReason = choice.optString("finish_reason", null);

        // Model from response
        String model = json.optString("model", null);

        // Token usage
        int promptTokens = 0;
        int completionTokens = 0;
        JSONObject usage = json.optJSONObject("usage");
        if (usage != null) {
            promptTokens = usage.optInt("prompt_tokens", 0);
            completionTokens = usage.optInt("completion_tokens", 0);
        }

        // Tool calls (if present)
        JSONArray toolCallsArr = message.optJSONArray("tool_calls");
        List<LlmRequest.ToolCall> toolCalls = null;
        if (toolCallsArr != null && toolCallsArr.length() > 0) {
            toolCalls = new ArrayList<>();
            for (int i = 0; i < toolCallsArr.length(); i++) {
                JSONObject tc = toolCallsArr.getJSONObject(i);
                JSONObject fn = tc.getJSONObject("function");
                toolCalls.add(new LlmRequest.ToolCall(
                        tc.getString("id"),
                        tc.optString("type", "function"),
                        new LlmRequest.FunctionCall(
                                fn.getString("name"),
                                fn.getString("arguments")
                        )
                ));
            }
        }

        return new LlmResponse(content, finishReason, promptTokens,
                completionTokens, model, toolCalls);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public SseParser createSseParser() {
        return new OpenAISseParser();
    }

    @Override
    public String unsupportedMessage() {
        return null; // OpenAI-compatible format is supported
    }
}
