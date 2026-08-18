package com.openterface.keymod.agent.llm;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * SSE parser for Google Gemini's streaming API format.
 *
 * <p>Gemini's SSE format ({@code streamGenerateContent?alt=sse}) sends each chunk
 * as a complete JSON object (no {@code event:} lines). Each data payload is a
 * full streaming response:
 * <pre>
 * data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}
 * data: {"candidates":[{"content":{"parts":[{"text":"Hello world"}]},
 *        "finishReason":"STOP"}],"usageMetadata":{...}}
 * </pre>
 *
 * <p>Important: Gemini sends ACCUMULATED text (not deltas) in each chunk.
 * This parser computes the delta by tracking the previously seen text.
 *
 * <p>Stream completion is signaled by {@code finishReason} being non-null
 * (typically "STOP"). There is no {@code [DONE]} marker.
 */
public final class GeminiSseParser extends SseParser {

    private static final String TAG = "GeminiSseParser";
    private static final String DATA_PREFIX = "data: ";

    /** All text seen so far — Gemini sends accumulated text per chunk, we compute deltas */
    private String previousText = "";

    /** Token usage from the last chunk that contains usageMetadata */
    private int promptTokens;
    private int completionTokens;

    @Override
    public LlmResponse parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        if (!line.startsWith(DATA_PREFIX)) return null;

        String payload = line.substring(DATA_PREFIX.length()).trim();
        if (payload.isEmpty()) return null;

        try {
            JSONObject json = new JSONObject(payload);

            // Extract text from candidates[0].content.parts[0].text
            String accumulatedText = "";
            String finishReason = null;

            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);

                // Extract text parts
                JSONObject content = candidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        JSONObject firstPart = parts.getJSONObject(0);
                        accumulatedText = firstPart.optString("text", "");
                    }
                }

                // Check finish reason
                finishReason = candidate.optString("finishReason", null);
            }

            // Compute delta (Gemini sends accumulated text, we need just the new part)
            String delta;
            if (accumulatedText.startsWith(previousText)) {
                delta = accumulatedText.substring(previousText.length());
            } else {
                // Text doesn't continue previous — use as-is (shouldn't normally happen)
                delta = accumulatedText;
            }
            previousText = accumulatedText;

            // Extract token usage (usually in the last chunk)
            JSONObject usageMetadata = json.optJSONObject("usageMetadata");
            if (usageMetadata != null) {
                promptTokens = usageMetadata.optInt("promptTokenCount", 0);
                completionTokens = usageMetadata.optInt("candidatesTokenCount", 0);
            }

            // Map Gemini finish reasons to standard ones
            String mappedFinish = mapFinishReason(finishReason);

            if (mappedFinish != null) {
                // Stream is done — include any remaining delta text in the
                // terminal response. The previous implementation returned a
                // terminal LlmResponse with an empty delta, silently dropping
                // the last text chunk when finishReason and text arrived
                // together in the same SSE payload.
                return LlmResponse.terminal(delta, mappedFinish,
                        promptTokens, completionTokens);
            }

            if (!delta.isEmpty()) {
                return LlmResponse.delta(delta);
            }

            return null;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse Gemini SSE: " + payload, e);
            return null;
        }
    }

    @Override
    public int getPromptTokens() {
        return promptTokens;
    }

    @Override
    public int getCompletionTokens() {
        return completionTokens;
    }

    @Override
    public void reset() {
        super.reset();
        previousText = "";
        promptTokens = 0;
        completionTokens = 0;
    }

    /**
     * Map Gemini finish reasons to standard finish reason strings.
     * Package-private so {@link GoogleAdapter} can reuse for non-streaming responses.
     *
     * @param geminiReason Gemini finish reason (e.g. "STOP", "MAX_TOKENS")
     * @return standard finish reason, or null if not finished
     */
    static String mapFinishReason(String geminiReason) {
        if (geminiReason == null || geminiReason.isEmpty()) {
            return null;
        }
        switch (geminiReason) {
            case "STOP":
                return "stop";
            case "MAX_TOKENS":
                return "length";
            case "SAFETY":
            case "RECITATION":
            case "OTHER":
                return geminiReason.toLowerCase();
            default:
                return geminiReason.toLowerCase();
        }
    }
}
