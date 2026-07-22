package com.openterface.keymod.agent.llm;

import org.json.JSONObject;

/**
 * Parses Server-Sent Events (SSE) from OpenAI-compatible streaming responses.
 *
 * Protocol:
 *   - Each event starts with "data: " prefix
 *   - "[DONE]" signals end of stream
 *   - Each data payload is JSON with choices[0].delta.content
 *   - choices[0].finish_reason present on terminal event
 */
public final class SseParser {

    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";

    /**
     * Parse a single SSE data line into an LlmResponse.
     *
     * @param line raw line from the SSE stream (e.g. "data: {...}")
     * @return parsed response, or null if the line should be skipped
     *         (blank lines, comments, keep-alive pings)
     * @throws StreamDoneException if stream is complete ([DONE] received)
     */
    public static LlmResponse parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null; // skip blank lines
        }

        if (!line.startsWith(DATA_PREFIX)) {
            return null; // skip non-data lines (e.g. "event:", "id:", "retry:")
        }

        String payload = line.substring(DATA_PREFIX.length()).trim();

        if (DONE_MARKER.equals(payload)) {
            throw new StreamDoneException();
        }

        try {
            JSONObject json = new JSONObject(payload);
            JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
            JSONObject delta = choice.optJSONObject("delta");

            if (delta == null) {
                // Some providers send choices[0] without delta on terminal event
                String finishReason = choice.optString("finish_reason", null);
                if (finishReason != null) {
                    return LlmResponse.terminal(finishReason);
                }
                return null;
            }

            String content = delta.optString("content", "");
            String finishReason = choice.optString("finish_reason", null);

            return new LlmResponse(content, finishReason, 0, 0);
        } catch (org.json.JSONException e) {
            // Malformed JSON — skip the line, log in caller
            return null;
        }
    }

    /** Thrown when [DONE] marker is received */
    public static final class StreamDoneException extends RuntimeException {
        public StreamDoneException() {
            super("SSE stream completed ([DONE] received)");
        }
    }
}
