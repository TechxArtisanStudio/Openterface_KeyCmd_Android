package com.openterface.keymod.agent.llm;

/**
 * SSE parser for OpenAI-compatible streaming responses.
 *
 * <p>Protocol:
 * <ul>
 *   <li>Each event starts with "data: " prefix</li>
 *   <li>"[DONE]" signals end of stream</li>
 *   <li>Each data payload is JSON with choices[0].delta.content and/or delta.tool_calls</li>
 *   <li>choices[0].finish_reason present on terminal event</li>
 * </ul>
 *
 * <p>Tool calls are streamed incrementally — multiple SSE events contribute fragments
 * to the same tool call. Use {@link #parseLine(String)} for per-event parsing
 * and {@link #collectToolCalls()} after the stream ends to get the assembled calls.
 *
 * <p>This is the default parser for OpenAI, Mistral, Groq, DashScope, DeepSeek, and
 * other OpenAI-compatible providers.
 */
public final class OpenAISseParser extends SseParser {
    // All logic inherited from SseParser base class.
    // This class exists to make the OpenAI implementation explicit
    // and to allow SseParser to become abstract in the future.
}
