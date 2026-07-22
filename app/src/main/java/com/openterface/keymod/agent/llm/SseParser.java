package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Server-Sent Events (SSE) from OpenAI-compatible streaming responses.
 *
 * Protocol:
 *   - Each event starts with "data: " prefix
 *   - "[DONE]" signals end of stream
 *   - Each data payload is JSON with choices[0].delta.content and/or delta.tool_calls
 *   - choices[0].finish_reason present on terminal event
 *
 * Tool calls are streamed incrementally — multiple SSE events contribute fragments
 * to the same tool call. Use {@link #parseLine(String)} for per-event parsing
 * and {@link #collectToolCalls()} after the stream ends to get the assembled calls.
 */
public final class SseParser {

    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";

    /** Accumulated tool call fragments (assembled across multiple SSE events) */
    private final List<JSONObject> rawToolCalls = new ArrayList<>();

    /**
     * Parse a single SSE data line into an LlmResponse.
     *
     * @param line raw line from the SSE stream (e.g. "data: {...}")
     * @return parsed response, or null if the line should be skipped
     *         (blank lines, comments, keep-alive pings)
     * @throws StreamDoneException if stream is complete ([DONE] received)
     */
    public LlmResponse parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        if (!line.startsWith(DATA_PREFIX)) {
            return null;
        }

        String payload = line.substring(DATA_PREFIX.length()).trim();

        if (DONE_MARKER.equals(payload)) {
            throw new StreamDoneException();
        }

        try {
            JSONObject json = new JSONObject(payload);
            JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
            JSONObject delta = choice.optJSONObject("delta");

            // --- Handle tool call fragments ---
            if (delta != null && delta.has("tool_calls")) {
                JSONArray toolCallDeltas = delta.getJSONArray("tool_calls");
                for (int i = 0; i < toolCallDeltas.length(); i++) {
                    JSONObject fragment = toolCallDeltas.getJSONObject(i);
                    int index = fragment.optInt("index", -1);

                    // Expand the accumulator if needed
                    while (rawToolCalls.size() <= index) {
                        rawToolCalls.add(new JSONObject());
                    }

                    JSONObject existing = rawToolCalls.get(index);

                    if (fragment.has("id")) {
                        existing.put("id", fragment.getString("id"));
                    }
                    if (fragment.has("type")) {
                        existing.put("type", fragment.getString("type"));
                    }
                    if (fragment.has("function")) {
                        JSONObject fnFragment = fragment.getJSONObject("function");
                        JSONObject existingFn = existing.optJSONObject("function");
                        if (existingFn == null) {
                            existingFn = new JSONObject();
                            existing.put("function", existingFn);
                        }
                        if (fnFragment.has("name")) {
                            existingFn.put("name", fnFragment.getString("name"));
                        }
                        if (fnFragment.has("arguments")) {
                            // Arguments are streamed incrementally — concatenate
                            String existingArgs = existingFn.optString("arguments", "");
                            existingFn.put("arguments", existingArgs + fnFragment.getString("arguments"));
                        }
                    }
                }
            }

            if (delta == null) {
                // Terminal event without delta
                String finishReason = choice.optString("finish_reason", null);
                if (finishReason != null) {
                    return LlmResponse.terminal(finishReason);
                }
                return null;
            }

            // --- Handle text content ---
            String content = delta.optString("content", "");
            String finishReason = choice.optString("finish_reason", null);

            return new LlmResponse(content, finishReason, 0, 0);
        } catch (JSONException e) {
            // Malformed JSON — skip the line
            return null;
        }
    }

    /**
     * After the stream ends, convert accumulated raw tool call fragments
     * into typed ToolCall objects.
     *
     * @return list of assembled tool calls (empty if none)
     */
    public List<LlmRequest.ToolCall> collectToolCalls() {
        List<LlmRequest.ToolCall> result = new ArrayList<>();
        for (JSONObject raw : rawToolCalls) {
            JSONObject fn = raw.optJSONObject("function");
            if (fn == null) continue;

            result.add(new LlmRequest.ToolCall(
                    raw.optString("id", ""),
                    raw.optString("type", "function"),
                    new LlmRequest.FunctionCall(
                            fn.optString("name", ""),
                            fn.optString("arguments", "")
                    )
            ));
        }
        return result;
    }

    /** Reset accumulated state (for parser reuse) */
    public void reset() {
        rawToolCalls.clear();
    }

    /** Thrown when [DONE] marker is received */
    public static final class StreamDoneException extends RuntimeException {
        public StreamDoneException() {
            super("SSE stream completed ([DONE] received)");
        }
    }
}
