package com.openterface.keymod.agent.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages multi-turn conversation history for Agent interactions.
 *
 * Maintains an ordered list of messages and builds complete LlmRequest
 * objects with system prompt prepended.
 *
 * <p>Usage:
 * <pre>
 *   ConversationManager cm = new ConversationManager("You are a helpful assistant.");
 *   cm.addUserMessage("What's the weather?");
 *   LlmRequest req = cm.buildRequest("gpt-4o-mini");
 *   // ... send to LLM ...
 *   cm.addAssistantMessage("The weather is sunny.");
 *   cm.addUserMessage("What about tomorrow?");
 *   LlmRequest req2 = cm.buildRequest("gpt-4o-mini");
 *   // History now contains: system, user1, assistant1, user2
 * </pre>
 *
 * <p>Thread safety: NOT thread-safe. Use on a single thread.
 */
public final class ConversationManager {

    /** Rough tokens-per-character estimate for context trimming */
    private static final double CHARS_PER_TOKEN = 4.0;

    private final String systemPrompt;
    private final List<LlmRequest.Message> history = new ArrayList<>();
    private int maxContextTokens = Integer.MAX_VALUE;

    /**
     * @param systemPrompt the system prompt prepended to every request
     */
    public ConversationManager(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    // ── Message management ─────────────────────────────────────────────

    /** Add a user message to the conversation history. */
    public void addUserMessage(String content) {
        history.add(new LlmRequest.Message("user", content));
    }

    /** Add an assistant message to the conversation history. */
    public void addAssistantMessage(String content) {
        history.add(new LlmRequest.Message("assistant", content));
    }

    /** Add an assistant message with tool calls. */
    public void addAssistantToolCalls(String content, List<LlmRequest.ToolCall> toolCalls) {
        history.add(new LlmRequest.Message("assistant", content, null, null, toolCalls));
    }

    /** Add a tool result message. */
    public void addToolResult(String toolCallId, String content) {
        history.add(new LlmRequest.Message("tool", content, null, toolCallId, null));
    }

    // ── Request building ───────────────────────────────────────────────

    /**
     * Build a complete LlmRequest with system prompt + conversation history.
     *
     * @param model the model to use
     * @return request ready to send
     */
    public LlmRequest buildRequest(String model) {
        LlmRequest request = new LlmRequest(model);

        // System prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            request.addSystemMessage(systemPrompt);
        }

        // Conversation history
        request.messages.addAll(history);

        return request;
    }

    /**
     * Build a request with a temporary system prompt override.
     */
    public LlmRequest buildRequest(String model, String systemPromptOverride) {
        LlmRequest request = new LlmRequest(model);
        if (systemPromptOverride != null && !systemPromptOverride.isEmpty()) {
            request.addSystemMessage(systemPromptOverride);
        }
        for (LlmRequest.Message msg : history) {
            request.messages.add(msg);
        }
        return request;
    }

    // ── History inspection ─────────────────────────────────────────────

    /** Get an unmodifiable view of the conversation history (excludes system prompt). */
    public List<LlmRequest.Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Number of messages in history (excludes system prompt). */
    public int size() {
        return history.size();
    }

    /** Clear all conversation history. */
    public void clear() {
        history.clear();
    }

    // ── Context management ─────────────────────────────────────────────

    /**
     * Set the maximum context window in tokens.
     * When exceeded, oldest messages are trimmed.
     */
    public void setMaxContextTokens(int maxTokens) {
        this.maxContextTokens = maxTokens;
    }

    /**
     * Estimate current token count (rough: characters / 4).
     * Does not include the system prompt.
     */
    public int estimateTokenCount() {
        int chars = 0;
        for (LlmRequest.Message msg : history) {
            if (msg.content != null) chars += msg.content.length();
        }
        return (int) (chars / CHARS_PER_TOKEN);
    }

    /**
     * If history exceeds maxContextTokens, remove oldest messages
     * until under the limit. Always preserves at least the last 2 messages
     * (most recent user + assistant exchange).
     */
    public void trimIfNeeded() {
        if (maxContextTokens == Integer.MAX_VALUE) return;

        while (estimateTokenCount() > maxContextTokens && history.size() > 2) {
            history.remove(0);
        }
    }
}
