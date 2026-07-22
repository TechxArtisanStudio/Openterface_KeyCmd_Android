package com.openterface.keymod.agent.llm;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * HTTP client for LLM API calls.
 *
 * Supports:
 *   - Non-streaming: chatSync() — blocks until full response
 *   - Streaming:     chatStream() — reads SSE line by line
 *   - Connection test: testConnection() — lightweight ping
 *
 * Thread safety: NOT thread-safe. Create one instance per call or synchronize externally.
 * Typical usage: instantiate on background thread via ExecutorService.
 */
public final class LlmHttpClient {
    private static final String TAG = "LlmHttpClient";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    /** Providers that do NOT use OpenAI-compatible format */
    private static final Set<String> UNSUPPORTED_PROVIDERS =
            new HashSet<>(Arrays.asList("Anthropic", "Google"));

    private final String apiKey;
    private final String endpoint;

    /**
     * @param apiKey  API key (may be empty for local/custom providers)
     * @param endpoint base URL without trailing slash, e.g. "https://api.openai.com/v1"
     */
    public LlmHttpClient(String apiKey, String endpoint) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.endpoint = endpoint != null && endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }

    // ── Connection Test ────────────────────────────────────────────────

    /**
     * Lightweight connection test — sends a 1-token completion request.
     * Returns the model name from the response if successful.
     *
     * @param model model to test with
     * @param providerName provider name for unsupported-provider check
     * @return response model name (proves the API key and endpoint work)
     * @throws Exception on any failure
     */
    public String testConnection(String model, String providerName) throws Exception {
        checkProviderSupported(providerName);

        LlmRequest request = new LlmRequest(model);
        request.addUserMessage("Hi");
        request.maxTokens = 1;

        chatSync(request);
        return model;
    }

    // ── Non-Streaming ──────────────────────────────────────────────────

    /**
     * Synchronous (non-streaming) chat completion.
     * Blocks the calling thread until the full response is received.
     */
    public LlmResponse chatSync(LlmRequest request) throws Exception {
        String url = endpoint + "/chat/completions";

        HttpURLConnection conn = openConnection(url);
        try {
            writeRequestBody(conn, request.toJson(false).toString());

            int code = conn.getResponseCode();
            if (code == 200) {
                String body = readStream(conn);
                return parseNonStreamingResponse(body);
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
        String url = endpoint + "/chat/completions";

        HttpURLConnection conn = null;
        try {
            conn = openConnection(url);
            writeRequestBody(conn, request.toJson(true).toString());

            int code = conn.getResponseCode();
            if (code != 200) {
                String errorBody = readErrorStream(conn);
                callback.onError(new LlmApiException(code, errorBody));
                return;
            }

            // Read SSE stream line by line
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder fullContent = new StringBuilder();

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    LlmResponse chunk = SseParser.parseLine(line);
                    if (chunk == null) continue;

                    // Accumulate content
                    if (!chunk.content.isEmpty()) {
                        fullContent.append(chunk.content);
                    }

                    callback.onChunk(chunk);

                    if (chunk.isTerminal()) {
                        break;
                    }
                }
            } catch (SseParser.StreamDoneException e) {
                // Normal end of SSE stream — expected
                Log.d(TAG, "SSE stream ended normally");
            } finally {
                reader.close();
            }

            LlmResponse finalResponse = new LlmResponse(
                    fullContent.toString(), "stop", 0, 0);
            callback.onComplete(finalResponse);

        } catch (Exception e) {
            Log.e(TAG, "Streaming chat error", e);
            callback.onError(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ── Internal Helpers ───────────────────────────────────────────────

    private void checkProviderSupported(String providerName) throws UnsupportedProviderException {
        if (providerName != null && UNSUPPORTED_PROVIDERS.contains(providerName)) {
            throw new UnsupportedProviderException(
                    providerName + " uses a non-OpenAI-compatible API format. " +
                    "This will be supported in a future update.");
        }
    }

    private HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (!apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, String json) throws Exception {
        byte[] bytes = json.getBytes("UTF-8");
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();
    }

    private String readStream(HttpURLConnection conn) throws Exception {
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
        } catch (Exception e) {
            return "(failed to read error body)";
        }
    }

    private LlmResponse parseNonStreamingResponse(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        String content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        String finishReason = json.getJSONArray("choices")
                .getJSONObject(0)
                .optString("finish_reason", null);

        int promptTokens = 0;
        int completionTokens = 0;
        JSONObject usage = json.optJSONObject("usage");
        if (usage != null) {
            promptTokens = usage.optInt("prompt_tokens", 0);
            completionTokens = usage.optInt("completion_tokens", 0);
        }

        return new LlmResponse(content, finishReason, promptTokens, completionTokens);
    }

    // ── Exception Types ────────────────────────────────────────────────

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

    /** Thrown when provider uses a non-OpenAI-compatible format */
    public static final class UnsupportedProviderException extends Exception {
        public UnsupportedProviderException(String message) {
            super(message);
        }
    }
}
