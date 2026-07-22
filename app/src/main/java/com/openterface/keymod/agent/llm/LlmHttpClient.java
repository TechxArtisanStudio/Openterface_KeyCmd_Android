package com.openterface.keymod.agent.llm;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP client for LLM API calls.
 *
 * Supports:
 *   - Non-streaming: chatSync() — blocks until full response
 *   - Streaming:     chatStream() — reads SSE line by line
 *   - Connection test: testConnection() — lightweight ping
 *
 * Provider-agnostic: delegates URL building, auth headers, body serialization,
 * and response parsing to a {@link ProviderAdapter}.
 *
 * Thread safety: NOT thread-safe. Create one instance per call or synchronize externally.
 * Typical usage: instantiate on background thread via ExecutorService.
 */
public final class LlmHttpClient {
    private static final String TAG = "LlmHttpClient";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    private final String apiKey;
    private final String endpoint;
    private final ProviderAdapter adapter;

    /**
     * @param apiKey  API key (may be empty for local/custom providers)
     * @param endpoint base URL without trailing slash, e.g. "https://api.openai.com/v1"
     */
    public LlmHttpClient(String apiKey, String endpoint) {
        this(apiKey, endpoint, new OpenAIAdapter());
    }

    /**
     * @param apiKey  API key (may be empty for local/custom providers)
     * @param endpoint base URL without trailing slash
     * @param adapter  provider-specific adapter
     */
    public LlmHttpClient(String apiKey, String endpoint, ProviderAdapter adapter) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.endpoint = endpoint != null && endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        this.adapter = adapter;
    }

    // ── Connection Test ────────────────────────────────────────────────

    /**
     * Lightweight connection test — sends a 1-token completion request.
     *
     * @param model model to test with
     * @param providerName provider display name (for adapter lookup)
     * @return requested model name (call succeeded without exception)
     * @throws Exception on any failure
     */
    public String testConnection(String model, String providerName) throws Exception {
        ProviderAdapter resolved = resolveAdapter(providerName);
        checkProviderSupported(resolved);

        LlmRequest request = new LlmRequest(model);
        request.addUserMessage("Hi");
        request.maxTokens = 1;

        chatSync(request, resolved);
        return model;
    }

    // ── Non-Streaming ──────────────────────────────────────────────────

    /** Synchronous chat completion using the default adapter. */
    public LlmResponse chatSync(LlmRequest request) throws Exception {
        return chatSync(request, adapter);
    }

    /** Synchronous chat completion with explicit adapter. */
    public LlmResponse chatSync(LlmRequest request, ProviderAdapter adapter) throws Exception {
        String url = adapter.buildUrl(endpoint);

        HttpURLConnection conn = openConnection(url, adapter);
        try {
            writeRequestBody(conn, adapter.buildBody(request, false).toString());

            int code = conn.getResponseCode();
            if (code == 200) {
                String body = readStream(conn);
                return adapter.parseNonStreamingResponse(body);
            } else {
                String errorBody = readErrorStream(conn);
                throw new LlmApiException(code, errorBody);
            }
        } finally {
            conn.disconnect();
        }
    }

    // ── Streaming ──────────────────────────────────────────────────────

    /**
     * Streaming chat completion — reads SSE events and invokes callback.
     *
     * IMPORTANT: This method blocks the calling thread. Run it on ExecutorService.
     * The LlmResult callbacks are invoked on the SAME background thread.
     * Caller must post to main Handler for UI updates.
     */
    public void chatStream(LlmRequest request, LlmResult callback) {
        chatStream(request, adapter, callback);
    }

    /** Streaming chat with explicit adapter. */
    public void chatStream(LlmRequest request, ProviderAdapter adapter, LlmResult callback) {
        if (!adapter.supportsStreaming()) {
            callback.onError(new UnsupportedOperationException(
                    adapter.name() + " does not support streaming."));
            return;
        }

        String url = adapter.buildUrl(endpoint);

        HttpURLConnection conn = null;
        try {
            conn = openConnection(url, adapter);
            writeRequestBody(conn, adapter.buildBody(request, true).toString());

            int code = conn.getResponseCode();
            if (code != 200) {
                String errorBody = readErrorStream(conn);
                callback.onError(new LlmApiException(code, errorBody));
                return;
            }

            // Read SSE stream line by line
            SseParser parser = new SseParser();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder fullContent = new StringBuilder();

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    LlmResponse chunk = parser.parseLine(line);
                    if (chunk == null) continue;

                    if (!chunk.content.isEmpty()) {
                        fullContent.append(chunk.content);
                    }

                    callback.onChunk(chunk);

                    if (chunk.isTerminal()) {
                        break;
                    }
                }
            } catch (SseParser.StreamDoneException e) {
                Log.d(TAG, "SSE stream ended normally");
            } finally {
                reader.close();
            }

            // Assemble tool calls from accumulated fragments
            java.util.List<LlmRequest.ToolCall> toolCalls = parser.collectToolCalls();
            String finishReason = toolCalls.isEmpty() ? "stop" : "tool_calls";

            LlmResponse finalResponse = new LlmResponse(
                    fullContent.toString(), finishReason, 0, 0, null,
                    toolCalls.isEmpty() ? null : toolCalls);
            callback.onComplete(finalResponse);

        } catch (Exception e) {
            Log.e(TAG, "Streaming chat error", e);
            callback.onError(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ── Internal Helpers ───────────────────────────────────────────────

    /** Resolve adapter from provider name; falls back to OpenAI for unknown names. */
    private ProviderAdapter resolveAdapter(String providerName) {
        if (providerName != null) {
            return ProviderAdapterFactory.get(providerName);
        }
        return adapter;
    }

    private void checkProviderSupported(ProviderAdapter adapter)
            throws UnsupportedProviderException {
        String msg = adapter.unsupportedMessage();
        if (msg != null) {
            throw new UnsupportedProviderException(msg);
        }
    }

    private HttpURLConnection openConnection(String url, ProviderAdapter adapter)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        adapter.setAuthHeaders(conn, apiKey);
        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, String json) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
            os.flush();
        }
    }

    private String readStream(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            java.io.InputStream errStream = conn.getErrorStream();
            if (errStream == null) return "(no body)";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errStream, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            return "(failed to read error body)";
        }
    }

    // ─ Exception Types ────────────────────────────────────────────────

    /** Thrown when API returns non-200 status code */
    public static final class LlmApiException extends Exception {
        public final int httpCode;
        public final String responseBody;

        public LlmApiException(int httpCode, String responseBody) {
            super("API error " + httpCode + ": " +
                    responseBody.substring(0, Math.min(200, responseBody.length())));
            this.httpCode = httpCode;
            this.responseBody = responseBody;
        }
    }

    /** Thrown when provider uses a non-implemented API format */
    public static final class UnsupportedProviderException extends Exception {
        public UnsupportedProviderException(String message) {
            super(message);
        }
    }
}
