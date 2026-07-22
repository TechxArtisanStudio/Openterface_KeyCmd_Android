package com.openterface.keymod.agent.llm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for LlmHttpClient using a local Ollama instance.
 * Requires Ollama running on http://localhost:11434 with qwen2.5:7b.
 *
 * Run with:
 *   ./gradlew testDebugUnitTest --tests "com.openterface.keymod.agent.llm.LlmHttpClientIntegrationTest"
 */
@RunWith(RobolectricTestRunner.class)
public class LlmHttpClientIntegrationTest {

    private static final String OLLAMA_ENDPOINT = "http://localhost:11434/v1";
    private static final String TEST_MODEL = "qwen2.5:7b";

    @Test
    public void testConnectionSuccess() throws Exception {
        LlmHttpClient client = new LlmHttpClient("", OLLAMA_ENDPOINT);
        String model = client.testConnection(TEST_MODEL, "Custom");
        assertEquals(TEST_MODEL, model);
    }

    @Test
    public void testChatSync() throws Exception {
        LlmHttpClient client = new LlmHttpClient("", OLLAMA_ENDPOINT);
        LlmRequest req = new LlmRequest(TEST_MODEL);
        req.addUserMessage("Reply with only the word 'pong'");
        req.maxTokens = 10;

        LlmResponse resp = client.chatSync(req);
        assertNotNull("Response should not be null", resp);
        assertNotNull("Content should not be null", resp.content);
        assertFalse("Content should not be empty", resp.content.isEmpty());
        assertEquals("stop", resp.finishReason);
        System.out.println("=== chatSync response: " + resp.content);
    }

    @Test
    public void testChatStream() throws Exception {
        LlmHttpClient client = new LlmHttpClient("", OLLAMA_ENDPOINT);
        LlmRequest req = new LlmRequest(TEST_MODEL);
        req.addUserMessage("Reply with only the word 'pong'");
        req.maxTokens = 10;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LlmResponse> finalRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        StringBuilder streamed = new StringBuilder();

        client.chatStream(req, new LlmResult() {
            @Override
            public void onChunk(LlmResponse chunk) {
                if (!chunk.content.isEmpty()) {
                    streamed.append(chunk.content);
                }
            }
            @Override
            public void onComplete(LlmResponse fullResponse) {
                finalRef.set(fullResponse);
                latch.countDown();
            }
            @Override
            public void onError(Exception e) {
                errorRef.set(e);
                latch.countDown();
            }
        });

        boolean finished = latch.await(60, TimeUnit.SECONDS);
        assertTrue("Stream should complete within 60s", finished);
        assertNull("No error expected: " + errorRef.get(), errorRef.get());
        assertNotNull("Final response should not be null", finalRef.get());
        assertFalse("Streamed content should not be empty", streamed.toString().isEmpty());
        System.out.println("=== chatStream response: " + streamed);
    }

    @Test
    public void testInvalidEndpoint() {
        LlmHttpClient client = new LlmHttpClient("fake-key",
                "http://192.0.2.1:9999/v1"); // non-routable IP
        try {
            client.testConnection("gpt-4", "Custom");
            fail("Expected an exception for invalid endpoint");
        } catch (Exception e) {
            // Expected: connection error
            System.out.println("=== expected error: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    @Test
    public void testUnsupportedProvider() {
        LlmHttpClient client = new LlmHttpClient("key", OLLAMA_ENDPOINT);
        try {
            client.testConnection(TEST_MODEL, "Anthropic");
            fail("Expected UnsupportedProviderException");
        } catch (LlmHttpClient.UnsupportedProviderException e) {
            assertTrue(e.getMessage().contains("Anthropic"));
            System.out.println("=== expected error: " + e.getMessage());
        } catch (Exception e) {
            fail("Wrong exception type: " + e);
        }
    }
}
