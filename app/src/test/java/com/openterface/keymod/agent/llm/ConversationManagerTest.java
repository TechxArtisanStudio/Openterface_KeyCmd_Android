package com.openterface.keymod.agent.llm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for ConversationManager.
 */
@RunWith(RobolectricTestRunner.class)
public class ConversationManagerTest {

    @Test
    public void testBuildRequestWithSystemPrompt() {
        ConversationManager cm = new ConversationManager("You are helpful.");
        cm.addUserMessage("Hello");
        cm.addAssistantMessage("Hi there!");
        cm.addUserMessage("How are you?");

        LlmRequest req = cm.buildRequest("gpt-4o");
        assertEquals("gpt-4o", req.model);
        assertEquals(4, req.messages.size()); // system + 3 messages
        assertEquals("system", req.messages.get(0).role);
        assertEquals("You are helpful.", req.messages.get(0).content);
        assertEquals("user", req.messages.get(1).role);
        assertEquals("assistant", req.messages.get(2).role);
        assertEquals("user", req.messages.get(3).role);
    }

    @Test
    public void testBuildRequestWithoutSystemPrompt() {
        ConversationManager cm = new ConversationManager("");
        cm.addUserMessage("Hello");

        LlmRequest req = cm.buildRequest("gpt-4o");
        assertEquals(1, req.messages.size());
        assertEquals("user", req.messages.get(0).role);
    }

    @Test
    public void testBuildRequestWithSystemOverride() {
        ConversationManager cm = new ConversationManager("Default system");
        cm.addUserMessage("Hello");

        LlmRequest req = cm.buildRequest("gpt-4o", "Override system");
        assertEquals("Override system", req.messages.get(0).content);
        assertEquals(2, req.messages.size());
    }

    @Test
    public void testToolMessages() {
        ConversationManager cm = new ConversationManager("You have tools.");

        cm.addUserMessage("What's the weather?");
        cm.addAssistantMessage("");  // will have tool calls in real usage
        cm.addToolResult("call_1", "{\"temp\": 25}");
        cm.addAssistantMessage("It's 25°C in Beijing.");

        LlmRequest req = cm.buildRequest("gpt-4o");
        assertEquals(5, req.messages.size()); // system + 4
        assertEquals("tool", req.messages.get(3).role);
        assertEquals("call_1", req.messages.get(3).toolCallId);
    }

    @Test
    public void testClear() {
        ConversationManager cm = new ConversationManager("System");
        cm.addUserMessage("Hello");
        assertEquals(1, cm.size());

        cm.clear();
        assertEquals(0, cm.size());

        LlmRequest req = cm.buildRequest("gpt-4o");
        assertEquals(1, req.messages.size()); // only system prompt
    }

    @Test
    public void testTrimIfNeeded() {
        ConversationManager cm = new ConversationManager("System");
        cm.setMaxContextTokens(20);

        // Add messages that exceed the limit
        cm.addUserMessage("Hello");          // ~1 token
        cm.addAssistantMessage("Hi!");       // ~1 token
        cm.addUserMessage("Tell me a long story about the history of computing..."); // ~10 tokens
        cm.addAssistantMessage("Once upon a time in the world of computers...");     // ~8 tokens

        assertTrue(cm.estimateTokenCount() > 20);
        cm.trimIfNeeded();
        // Should have trimmed oldest messages, keeping at least 2
        assertTrue(cm.size() >= 2);
        assertTrue(cm.estimateTokenCount() <= 20 || cm.size() == 2);
    }

    @Test
    public void testGetHistoryIsUnmodifiable() {
        ConversationManager cm = new ConversationManager("System");
        cm.addUserMessage("Hello");

        try {
            cm.getHistory().add(new LlmRequest.Message("user", "injected"));
            fail("History should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
