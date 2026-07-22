package com.openterface.keymod.agent.llm;

import java.util.List;

/**
 * Represents either a complete response (non-streaming)
 * or a single SSE chunk (streaming).
 *
 * For non-streaming: content is the full reply text.
 * For streaming: content is the delta from one SSE event.
 *                finishReason is non-null only on the terminal chunk.
 */
public final class LlmResponse {
    public final String content;            // "" when no text delta
    public final String finishReason;       // null while streaming, "stop" at end
    public final int promptTokens;          // 0 for streaming chunks
    public final int completionTokens;      // 0 for streaming chunks
    public final String model;              // model from response (may be null)
    public final List<LlmRequest.ToolCall> toolCalls;  // tool calls (may be null)

    public LlmResponse(String content, String finishReason,
                       int promptTokens, int completionTokens) {
        this(content, finishReason, promptTokens, completionTokens, null, null);
    }

    public LlmResponse(String content, String finishReason,
                       int promptTokens, int completionTokens,
                       String model, List<LlmRequest.ToolCall> toolCalls) {
        this.content = content != null ? content : "";
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.model = model;
        this.toolCalls = toolCalls;
    }

    // ── Factory methods ──────────────────────────────────────────────

    /** Streaming delta with only text content */
    public static LlmResponse delta(String text) {
        return new LlmResponse(text, null, 0, 0, null, null);
    }

    /** Terminal streaming chunk */
    public static LlmResponse terminal(String finishReason) {
        return new LlmResponse("", finishReason, 0, 0, null, null);
    }

    /** Response containing tool calls (no text content) */
    public static LlmResponse withToolCalls(List<LlmRequest.ToolCall> toolCalls,
                                            String finishReason) {
        return new LlmResponse("", finishReason, 0, 0, null, toolCalls);
    }

    // ── Query helpers ────────────────────────────────────────────────

    public boolean isTerminal() {
        return finishReason != null;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
