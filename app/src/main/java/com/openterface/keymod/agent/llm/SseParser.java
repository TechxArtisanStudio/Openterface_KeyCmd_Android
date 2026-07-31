package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for SSE (Server-Sent Events) stream parsers.
 *
 * <p><b>Default implementation</b>: This class contains the OpenAI-compatible SSE
 * parsing logic (data prefix, [DONE] marker, choices[0].delta.content, tool call
 * fragment accumulation). Subclasses like {@link AnthropicSseParser} and
 * {@link GeminiSseParser} override {@link #parseLine(String)} for provider-specific
 * formats.
 *
 * <p><b>Usage</b>: Call {@link ProviderAdapter#createSseParser()} to get the correct
 * parser for each provider — do not instantiate {@code SseParser} directly.
 *
 * <p>Tool calls are streamed incrementally — multiple SSE events contribute fragments
 * to the same tool call. Use {@link #parseLine(String)} for per-event parsing
 * and {@link #collectToolCalls()} after the stream ends to get the assembled calls.
 */
public class SseParser {

    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";

    /** Accumulated tool call fragments (assembled across multiple SSE events) */
    private final List<JSONObject> rawToolCalls = new ArrayList<>();

    /**
     * Parse a single SSE data line into an LlmResponse.
     *
     * <p>Supports two formats:
     * <ol>
     *   <li>Standard SSE: lines prefixed with {@code "data: "} (OpenAI, Anthropic, etc.)</li>
     *   <li>Raw NDJSON: bare JSON lines without prefix (Ollama, some proxies)</li>
     * </ol>
     *
     * @param line raw line from the SSE stream
     * @return parsed response, or null if the line should be skipped
     * @throws StreamDoneException if stream is complete
     */
    public LlmResponse parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        String payload;

        if (line.startsWith(DATA_PREFIX)) {
            // Standard SSE format — strip "data: " prefix
            payload = line.substring(DATA_PREFIX.length()).trim();
        } else if (DONE_MARKER.equals(line.trim())) {
            // [DONE] marker without data: prefix (some providers)
            throw new StreamDoneException();
        } else if (line.trim().startsWith("{")) {
            // Raw NDJSON fallback (Ollama, some proxies) — parse directly
            payload = line.trim();
        } else {
            // Comment, blank, keep-alive, or other non-JSON line — skip
            return null;
        }

        if (DONE_MARKER.equals(payload)) {
            throw new StreamDoneException();
        }

        try {
            return parseJsonPayload(payload);
        } catch (JSONException e) {
            // Malformed JSON — skip the line
            return null;
        }
    }

    /**
     * Parse a JSON payload string (already stripped of SSE prefix) into an LlmResponse.
     * Handles content deltas, tool call fragments, and terminal events.
     */
    private LlmResponse parseJsonPayload(String payload) throws JSONException {
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

    /** Accumulated prompt tokens from the stream (0 if not available) */
    public int getPromptTokens() {
        return 0;
    }

    /** Accumulated completion tokens from the stream (0 if not available) */
    public int getCompletionTokens() {
        return 0;
    }

    /** Thrown when [DONE] marker is received */
    public static final class StreamDoneException extends RuntimeException {
        public StreamDoneException() {
            super("SSE stream completed ([DONE] received)");
        }
    }
}
