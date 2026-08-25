package com.openterface.keymod.agent.llm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for AnthropicSseParser — covers event-based SSE format
 * with message_start, content_block_delta, message_delta, message_stop, ping.
 */
@RunWith(RobolectricTestRunner.class)
public class AnthropicSseParserTest {

    private AnthropicSseParser parser;

    @Before
    public void setUp() {
        parser = new AnthropicSseParser();
    }

    @Test
    public void testContentBlockDelta() {
        // First, send message_start (so model info is available)
        parser.parseLine("event: message_start");
        parser.parseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01\",\"model\":\"claude-sonnet-4-20250514\",\"usage\":{\"input_tokens\":25}}}");

        // Now send content_block_delta
        parser.parseLine("event: content_block_delta");
        LlmResponse resp = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
        assertNotNull(resp);
        assertEquals("Hello", resp.content);
        assertNull(resp.finishReason);
        assertFalse(resp.isTerminal());
    }

    @Test
    public void testMessageStartExtractsInputTokens() {
        parser.parseLine("event: message_start");
        parser.parseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01\",\"model\":\"claude-sonnet-4-20250514\",\"usage\":{\"input_tokens\":42}}}");

        assertEquals(42, parser.getPromptTokens());
    }

    @Test(expected = SseParser.StreamDoneException.class)
    public void testMessageStopThrowsStreamDone() {
        parser.parseLine("event: message_stop");
        parser.parseLine("data: {\"type\":\"message_stop\"}");
    }

    @Test
    public void testPingIgnored() {
        parser.parseLine("event: ping");
        LlmResponse resp = parser.parseLine("data: {\"type\":\"ping\"}");
        assertNull("Ping should be ignored", resp);
    }

    @Test
    public void testMultipleDeltas() {
        // Simulate streaming: multiple content_block_delta events
        parser.parseLine("event: content_block_delta");
        LlmResponse r1 = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
        parser.parseLine("event: content_block_delta");
        LlmResponse r2 = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}");
        parser.parseLine("event: content_block_delta");
        LlmResponse r3 = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"!\"}}");

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        // Simulate what LlmHttpClient.chatStream does: concatenate deltas
        String fullText = r1.content + r2.content + r3.content;
        assertEquals("Hello world!", fullText);
    }

    @Test
    public void testMessageDeltaExtractsStopReasonAndOutputTokens() {
        // Send message_delta
        parser.parseLine("event: message_delta");
        LlmResponse resp = parser.parseLine(
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":15}}}");

        // message_delta should not produce output (it stores values internally)
        assertNull("message_delta should not return a response", resp);
        assertEquals(15, parser.getCompletionTokens());
    }

    @Test
    public void testFullStreamSequence() {
        // Complete Anthropic stream
        parser.parseLine("event: message_start");
        parser.parseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01\",\"model\":\"claude-sonnet-4-20250514\",\"usage\":{\"input_tokens\":10}}}");

        parser.parseLine("event: content_block_delta");
        LlmResponse r1 = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");

        parser.parseLine("event: content_block_delta");
        LlmResponse r2 = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" there\"}}");

        parser.parseLine("event: message_delta");
        parser.parseLine("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}");

        // Verify token counts
        assertEquals(10, parser.getPromptTokens());
        assertEquals(5, parser.getCompletionTokens());

        // Verify text concatenation
        assertEquals("Hi there", r1.content + r2.content);

        // message_stop should throw StreamDoneException
        parser.parseLine("event: message_stop");
        try {
            parser.parseLine("data: {\"type\":\"message_stop\"}");
            fail("Expected StreamDoneException");
        } catch (SseParser.StreamDoneException e) {
            // Expected
        }
    }

    @Test
    public void testBlankLineResetsEvent() {
        parser.parseLine("event: message_start");
        // Blank line between event and data should reset current event
        parser.parseLine("");
        // Data without preceding event should still be parsed by type field
        LlmResponse resp = parser.parseLine(
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"test\"}}");
        assertNotNull(resp);
        assertEquals("test", resp.content);
    }

    @Test
    public void testReset() {
        parser.parseLine("event: message_start");
        parser.parseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01\",\"model\":\"claude-sonnet-4-20250514\",\"usage\":{\"input_tokens\":42}}}");
        assertEquals(42, parser.getPromptTokens());

        parser.reset();
        assertEquals(0, parser.getPromptTokens());
        assertEquals(0, parser.getCompletionTokens());
    }

    @Test
    public void testMalformedJsonSkipped() {
        parser.parseLine("event: content_block_delta");
        LlmResponse resp = parser.parseLine("data: {bad json!!!");
        assertNull("Malformed JSON should be skipped", resp);
    }
}
