package com.openterface.keymod.agent.llm;

/**
 * Represents either a complete response (non-streaming)
 * or a single SSE chunk (streaming).
 *
 * For non-streaming: content is the full reply text.
 * For streaming: content is the delta from one SSE event.
 *                finishReason is non-null only on the terminal chunk.
 */
public final class LlmResponse {
    public final String content;        // "" when no text delta
    public final String finishReason;   // null while streaming, "stop" at end
    public final int promptTokens;      // 0 for streaming chunks
    public final int completionTokens;  // 0 for streaming chunks

    public LlmResponse(String content, String finishReason,
                       int promptTokens, int completionTokens) {
        this.content = content != null ? content : "";
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    /** Convenience: create a streaming delta with only text content */
    public static LlmResponse delta(String text) {
        return new LlmResponse(text, null, 0, 0);
    }

    /** Convenience: create the terminal streaming chunk */
    public static LlmResponse terminal(String finishReason) {
        return new LlmResponse("", finishReason, 0, 0);
    }

    public boolean isTerminal() {
        return finishReason != null;
    }
}
