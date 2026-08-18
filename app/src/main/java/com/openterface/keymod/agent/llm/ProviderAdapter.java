package com.openterface.keymod.agent.llm;

import java.net.HttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstracts the API differences between LLM providers.
 *
 * Each provider (OpenAI, Anthropic, Google, etc.) implements this interface.
 * LlmHttpClient depends only on this interface — adding a new provider
 * requires a new implementation, zero changes to the HTTP client.
 *
 * <p>Usage:
 * <pre>
 *   ProviderAdapter adapter = new OpenAIAdapter();
 *   LlmHttpClient client = new LlmHttpClient(apiKey, endpoint, adapter);
 * </pre>
 */
public interface ProviderAdapter {

    /** Human-readable provider name (e.g. "OpenAI", "Anthropic") */
    String name();

    /**
     * Build the full API URL from the base endpoint and model.
     *
     * @param endpoint base URL without trailing slash (e.g. "https://api.openai.com/v1")
     * @param model    model name (some providers embed model in URL)
     * @return complete URL for chat completions
     */
    String buildUrl(String endpoint, String model);

    /**
     * Build the full API URL for SSE streaming.
     *
     * <p>Default implementation returns {@link #buildUrl(String, String)} (most
     * providers use the same URL for streaming and non-streaming, toggling
     * streaming via the request body). Google Gemini overrides this because
     * its streaming endpoint is a different URL path
     * ({@code :streamGenerateContent?alt=sse}).
     *
     * @param endpoint base URL without trailing slash
     * @param model    model name
     * @return complete URL for streaming chat completions
     */
    default String buildStreamUrl(String endpoint, String model) {
        return buildUrl(endpoint, model);
    }

    /**
     * Set authentication headers on the connection.
     *
     * @param conn   the HTTP connection to configure
     * @param apiKey the API key (may be empty for local providers like Ollama)
     */
    void setAuthHeaders(HttpURLConnection conn, String apiKey);

    /**
     * Optionally append authentication info to the URL.
     * Default implementation returns URL unchanged. Google Gemini overrides
     * this to append {@code ?key=API_KEY} as a query parameter.
     *
     * @param url    the base URL from {@link #buildUrl}
     * @param apiKey the API key
     * @return the URL with auth appended if needed
     */
    default String appendAuthToUrl(String url, String apiKey) {
        return url;
    }

    /**
     * Build the JSON request body for the given LlmRequest.
     *
     * @param request the request model
     * @param stream  whether to enable streaming
     * @return JSON body ready to send
     */
    JSONObject buildBody(LlmRequest request, boolean stream) throws JSONException;

    /**
     * Parse a non-streaming (complete) API response into an LlmResponse.
     *
     * @param body the raw response body string
     * @return parsed response with content, token usage, etc.
     */
    LlmResponse parseNonStreamingResponse(String body) throws JSONException;

    /** Whether this provider supports SSE streaming */
    boolean supportsStreaming();

    /**
     * Create a provider-specific SSE parser for streaming responses.
     * Each provider has a different SSE format; this factory method
     * returns the appropriate parser.
     *
     * @return new SseParser instance for this provider
     */
    SseParser createSseParser();

    /**
     * Error message shown when this provider is not yet implemented.
     * Return null if the provider IS supported.
     */
    String unsupportedMessage();
}
