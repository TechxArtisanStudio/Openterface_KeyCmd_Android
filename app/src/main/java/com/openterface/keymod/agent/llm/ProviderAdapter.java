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
     * Build the full API URL from the base endpoint.
     *
     * @param endpoint base URL without trailing slash (e.g. "https://api.openai.com/v1")
     * @return complete URL for chat completions
     */
    String buildUrl(String endpoint);

    /**
     * Set authentication headers on the connection.
     *
     * @param conn   the HTTP connection to configure
     * @param apiKey the API key (may be empty for local providers like Ollama)
     */
    void setAuthHeaders(HttpURLConnection conn, String apiKey);

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
     * Error message shown when this provider is not yet implemented.
     * Return null if the provider IS supported.
     */
    String unsupportedMessage();
}
