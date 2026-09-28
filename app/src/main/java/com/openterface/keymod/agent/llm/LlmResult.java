package com.openterface.keymod.agent.llm;

/**
 * Callback for streaming LLM responses.
 * All methods are called on the background executor thread —
 * the caller must post to the main thread for UI updates.
 */
public interface LlmResult {
    /** Called for each SSE delta chunk */
    void onChunk(LlmResponse chunk);

    /** Called when the stream completes normally */
    void onComplete(LlmResponse fullResponse);

    /** Called on error — stream is terminated */
    void onError(Exception e);
}
