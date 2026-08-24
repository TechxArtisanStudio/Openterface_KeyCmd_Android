package com.openterface.keymod.agent.ui;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link AgentMessage} factory methods and field access.
 */
public class AgentMessageTest {

    // ── user() ─────────────────────────────────────────────────────────

    @Test
    public void testUserMessage() {
        AgentMessage msg = AgentMessage.user("Hello");
        assertEquals(AgentMessage.Type.USER, msg.type);
        assertEquals("Hello", msg.text.toString());
        assertTrue(msg.planSteps.isEmpty());
        assertTrue(msg.terminalLines.isEmpty());
        assertTrue(msg.macroSteps.isEmpty());
        assertFalse(msg.isHidMode);
        assertFalse(msg.isComplete);
        assertFalse(msg.isError);
        assertFalse(msg.canRetry);
        assertNull(msg.cliSuccess);
    }

    // ── assistant() ────────────────────────────────────────────────────

    @Test
    public void testAssistantMessage() {
        AgentMessage msg = AgentMessage.assistant("Hi there");
        assertEquals(AgentMessage.Type.ASSISTANT, msg.type);
        assertEquals("Hi there", msg.text.toString());
    }

    // ── assistantError() ───────────────────────────────────────────────

    @Test
    public void testAssistantErrorWithRetry() {
        AgentMessage msg = AgentMessage.assistantError("Connection failed", true);
        assertEquals(AgentMessage.Type.ASSISTANT, msg.type);
        assertEquals("Connection failed", msg.text.toString());
        assertTrue(msg.isError);
        assertTrue(msg.canRetry);
    }

    @Test
    public void testAssistantErrorWithoutRetry() {
        AgentMessage msg = AgentMessage.assistantError("Fatal error", false);
        assertTrue(msg.isError);
        assertFalse(msg.canRetry);
    }

    // ── error() (delegates to assistantError) ──────────────────────────

    @Test
    public void testErrorDelegatesToAssistantError() {
        AgentMessage msg = AgentMessage.error("Something broke", true);
        assertTrue(msg.isError);
        assertTrue(msg.canRetry);
        assertEquals("Something broke", msg.text.toString());
    }

    // ── plan() ─────────────────────────────────────────────────────────

    @Test
    public void testPlanMessage() {
        List<AgentPlanStep> steps = Arrays.asList(
                new AgentPlanStep(1, "Check disk", "df -h", AgentPlanStep.Kind.TERMINAL),
                new AgentPlanStep(2, "List files", "ls", AgentPlanStep.Kind.TERMINAL));
        AgentMessage msg = AgentMessage.plan(steps);

        assertEquals(AgentMessage.Type.PLAN, msg.type);
        assertNull(msg.text);
        assertEquals(2, msg.planSteps.size());
        assertFalse(msg.isHidMode);
    }

    @Test
    public void testPlanMessageWithHidMode() {
        List<AgentPlanStep> steps = Arrays.asList(
                new AgentPlanStep(1, "Type text", "hello", AgentPlanStep.Kind.HID));
        AgentMessage msg = AgentMessage.plan(steps, true);

        assertTrue(msg.isHidMode);
        assertEquals(1, msg.planSteps.size());
    }

    @Test
    public void testPlanStepsIsDefensiveCopy() {
        List<AgentPlanStep> original = new java.util.ArrayList<>();
        original.add(new AgentPlanStep(1, "Step", "cmd", AgentPlanStep.Kind.TERMINAL));
        AgentMessage msg = AgentMessage.plan(original);

        // Mutating original list should not affect message
        original.add(new AgentPlanStep(2, "Step2", "cmd2", AgentPlanStep.Kind.TERMINAL));
        assertEquals(1, msg.planSteps.size());
    }

    // ── actBar() ───────────────────────────────────────────────────────

    @Test
    public void testActBarMessage() {
        AgentMessage msg = AgentMessage.actBar();
        assertEquals(AgentMessage.Type.ACT_BAR, msg.type);
        assertNull(msg.text);
        assertTrue(msg.planSteps.isEmpty());
    }

    // ── executionCli() ─────────────────────────────────────────────────

    @Test
    public void testExecutionCliMessage() {
        List<String> lines = Arrays.asList("$ ls", "file1.txt", "file2.txt");
        AgentMessage msg = AgentMessage.executionCli(lines);

        assertEquals(AgentMessage.Type.EXECUTION_CLI, msg.type);
        assertEquals(3, msg.terminalLines.size());
        assertFalse(msg.isComplete);
        assertNull(msg.cliSuccess);
    }

    @Test
    public void testExecutionCliLinesIsDefensiveCopy() {
        List<String> original = new java.util.ArrayList<>(Arrays.asList("$ ls", "file.txt"));
        AgentMessage msg = AgentMessage.executionCli(original);

        original.add("extra.txt");
        assertEquals(2, msg.terminalLines.size());
    }

    // ── executionCliComplete() ─────────────────────────────────────────

    @Test
    public void testExecutionCliCompleteSuccess() {
        List<String> lines = Arrays.asList("$ ls", "file.txt");
        AgentMessage msg = AgentMessage.executionCliComplete(lines, true);

        assertEquals(AgentMessage.Type.EXECUTION_CLI, msg.type);
        assertTrue(msg.isComplete);
        assertTrue(msg.cliSuccess);
        assertFalse(msg.isError);
    }

    @Test
    public void testExecutionCliCompleteFailure() {
        List<String> lines = Arrays.asList("$ badcmd", "command not found");
        AgentMessage msg = AgentMessage.executionCliComplete(lines, false);

        assertTrue(msg.isComplete);
        assertFalse(msg.cliSuccess);
    }

    // ── executionMacro() ───────────────────────────────────────────────

    @Test
    public void testExecutionMacroMessage() {
        List<String> steps = Arrays.asList("Step 1", "Step 2", "Step 3");
        AgentMessage msg = AgentMessage.executionMacro(steps, 66, 2, "Sending...");

        assertEquals(AgentMessage.Type.EXECUTION_MACRO, msg.type);
        assertEquals(3, msg.macroSteps.size());
        assertEquals(66, msg.macroProgress);
        assertEquals(2, msg.macroCurrentStep);
        assertEquals("Sending...", msg.macroStatusChip);
    }

    @Test
    public void testExecutionMacroWithNullChip() {
        AgentMessage msg = AgentMessage.executionMacro(
                Arrays.asList("Step"), 100, 0, null);
        assertNull(msg.macroStatusChip);
    }

    // ── thinking() ─────────────────────────────────────────────────────

    @Test
    public void testThinkingMessage() {
        AgentMessage msg = AgentMessage.thinking("Analyzing...");
        assertEquals(AgentMessage.Type.THINKING, msg.type);
        assertEquals("Analyzing...", msg.text.toString());
    }
}
