package com.openterface.keymod.agent.llm;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SSE parser for Anthropic's streaming API format.
 *
 * <p>Anthropic uses event-based SSE with these event types:
 * <ul>
 *   <li>{@code message_start} — initial metadata (model, input tokens)</li>
 *   <li>{@code content_block_delta} — incremental text via {@code text_delta}</li>
 *   <li>{@code message_delta} — final metadata (stop_reason, output tokens)</li>
 *   <li>{@code message_stop} — stream complete</li>
 *   <li>{@code ping} — keep-alive (ignored)</li>
 * </ul>
 *
 * <p>Unlike OpenAI, Anthropic has no {@code [DONE]} marker.
 * Stream completion is signaled by {@code message_stop}.
 */
public final class AnthropicSseParser extends SseParser {

    private static final String TAG = "AnthropicSseParser";
    private static final String DATA_PREFIX = "data: ";
    private static final String EVENT_PREFIX = "event: ";

    private String currentEvent;
    private String model;
    private int inputTokens;
    private int outputTokens;
    private String stopReason;

    @Override
    public LlmResponse parseLine(String line) {
        if (line == null || line.isEmpty()) {
            currentEvent = null;
            return null;
        }

        // Track the current event type
        if (line.startsWith(EVENT_PREFIX)) {
            currentEvent = line.substring(EVENT_PREFIX.length()).trim();
            return null;
        }

        if (!line.startsWith(DATA_PREFIX)) {
            return null;
        }

        String payload = line.substring(DATA_PREFIX.length()).trim();
        if (payload.isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(payload);
            String type = json.optString("type", "");

            switch (type) {
                case "message_start": {
                    // Extract model and input token count
                    JSONObject message = json.optJSONObject("message");
                    if (message != null) {
                        model = message.optString("model", null);
                        JSONObject usage = message.optJSONObject("usage");
                        if (usage != null) {
                            inputTokens = usage.optInt("input_tokens", 0);
                        }
                    }
                    return null;
                }

                case "content_block_start": {
                    // Track tool_use blocks for future tool call support
                    JSONObject contentBlock = json.optJSONObject("content_block");
                    if (contentBlock != null
                            && "tool_use".equals(contentBlock.optString("type"))) {
                        // Tool use block starting — future: accumulate tool call fragments
                        Log.d(TAG, "Tool use block started: "
                                + contentBlock.optString("name", ""));
                    }
                    return null;
                }

                case "content_block_delta": {
                    JSONObject delta = json.optJSONObject("delta");
                    if (delta == null) return null;

                    String deltaType = delta.optString("type", "");

                    if ("text_delta".equals(deltaType)) {
                        String text = delta.optString("text", "");
                        if (!text.isEmpty()) {
                            return LlmResponse.delta(text);
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        // Tool use input JSON delta — future: accumulate arguments
                        String partialJson = delta.optString("partial_json", "");
                        if (!partialJson.isEmpty()) {
                            Log.d(TAG, "Tool input delta: " + partialJson);
                        }
                    }
                    return null;
                }

                case "message_delta": {
                    // Extract stop_reason and output tokens
                    JSONObject delta = json.optJSONObject("delta");
                    if (delta != null) {
                        stopReason = delta.optString("stop_reason", null);
                    }
                    JSONObject usage = json.optJSONObject("usage");
                    if (usage != null) {
                        outputTokens = usage.optInt("output_tokens", 0);
                    }
                    // Don't throw yet — wait for message_stop to end the stream
                    return null;
                }

                case "message_stop":
                    // Stream complete — throw to end the reading loop
                    throw new StreamDoneException();

                case "ping":
                case "content_block_stop":
                    // Ignored
                    return null;

                default:
                    return null;
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse Anthropic SSE: " + payload, e);
            return null;
        }
    }

    @Override
    public int getPromptTokens() {
        return inputTokens;
    }

    @Override
    public int getCompletionTokens() {
        return outputTokens;
    }

    @Override
    public void reset() {
        super.reset();
        currentEvent = null;
        model = null;
        inputTokens = 0;
        outputTokens = 0;
        stopReason = null;
    }
}
