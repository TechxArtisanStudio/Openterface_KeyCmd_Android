package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;

import com.openterface.keymod.agent.ui.AgentMessage;
import com.openterface.keymod.agent.ui.AgentPlanStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link AgentSession}.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentSessionTest {

    private AgentSession session;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        session = new AgentSession(ctx);
    }

    // ── Initial state ──────────────────────────────────────────────────

    @Test
    public void testInitialStateEmpty() {
        assertEquals(0, session.size());
        assertTrue(session.getMessages().isEmpty());
    }

    // ── Adding messages ────────────────────────────────────────────────

    @Test
    public void testAddUserMessage() {
        session.addUserMessage("Hello");
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.USER, session.getMessages().get(0).type);
        assertEquals("Hello", session.getMessages().get(0).text.toString());
    }

    @Test
    public void testAddAssistantMessage() {
        session.addAssistantMessage("Hi there");
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.ASSISTANT, session.getMessages().get(0).type);
        assertEquals("Hi there", session.getMessages().get(0).text.toString());
    }

    @Test
    public void testAddPlanMessage() {
        List<AgentPlanStep> steps = Arrays.asList(
                new AgentPlanStep(1, "Step 1", "ls", AgentPlanStep.Kind.TERMINAL));
        session.addPlanMessage(steps);
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.PLAN, session.getMessages().get(0).type);
        assertEquals(1, session.getMessages().get(0).planSteps.size());
    }

    @Test
    public void testAddActBarMessage() {
        session.addActBarMessage();
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.ACT_BAR, session.getMessages().get(0).type);
    }

    @Test
    public void testAddExecutionCliMessage() {
        session.addExecutionCliMessage(Arrays.asList("$ ls", "file1.txt", "file2.txt"));
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.EXECUTION_CLI, session.getMessages().get(0).type);
        assertEquals(3, session.getMessages().get(0).terminalLines.size());
    }

    @Test
    public void testAddExecutionMacroMessage() {
        session.addExecutionMacroMessage(
                Arrays.asList("Step 1", "Step 2"), 50, 1, "Sending...");
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.EXECUTION_MACRO, session.getMessages().get(0).type);
        assertEquals(50, session.getMessages().get(0).macroProgress);
        assertEquals(1, session.getMessages().get(0).macroCurrentStep);
    }

    @Test
    public void testAddRawMessage() {
        AgentMessage msg = AgentMessage.thinking("Processing...");
        session.addMessage(msg);
        assertEquals(1, session.size());
        assertEquals(AgentMessage.Type.THINKING, session.getMessages().get(0).type);
    }

    // ── Multiple messages ──────────────────────────────────────────────

    @Test
    public void testMultipleMessages() {
        session.addUserMessage("Q1");
        session.addAssistantMessage("A1");
        session.addUserMessage("Q2");
        assertEquals(3, session.size());
        assertEquals("Q1", session.getMessages().get(0).text.toString());
        assertEquals("A1", session.getMessages().get(1).text.toString());
        assertEquals("Q2", session.getMessages().get(2).text.toString());
    }

    // ── getMessages() is unmodifiable ──────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMessagesIsUnmodifiable() {
        session.addUserMessage("Hello");
        session.getMessages().add(AgentMessage.assistant("hack"));
    }

    // ── clear() ────────────────────────────────────────────────────────

    @Test
    public void testClear() {
        session.addUserMessage("Hello");
        session.addAssistantMessage("Hi");
        assertEquals(2, session.size());

        session.clear();
        assertEquals(0, session.size());
        assertTrue(session.getMessages().isEmpty());
    }

    // ── save() + load() persistence ────────────────────────────────────

    @Test
    public void testSaveAndLoad() {
        session.addUserMessage("Hello");
        session.addAssistantMessage("Hi there");
        session.save();

        // Create a new session pointing to same prefs
        AgentSession session2 = new AgentSession(RuntimeEnvironment.getApplication());
        assertEquals(0, session2.size()); // fresh instance has no in-memory messages

        session2.load();
        assertEquals(2, session2.size());
        assertEquals("Hello", session2.getMessages().get(0).text.toString());
        assertEquals("Hi there", session2.getMessages().get(1).text.toString());
    }

    @Test
    public void testLoadWithNoSavedData() {
        // Should not throw — no data in prefs
        session.load();
        assertEquals(0, session.size());
    }

    @Test
    public void testLoadPreservesTypes() {
        session.addUserMessage("user text");
        session.addAssistantMessage("assistant text");
        session.save();

        AgentSession session2 = new AgentSession(RuntimeEnvironment.getApplication());
        session2.load();
        assertEquals(AgentMessage.Type.USER, session2.getMessages().get(0).type);
        assertEquals(AgentMessage.Type.ASSISTANT, session2.getMessages().get(1).type);
    }
}
