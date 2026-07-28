package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for AgentPlan and AgentPlan.Step.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentPlanTest {

    // ── Step factory: terminal ──────────────────────────────────────────

    @Test
    public void testTerminalStepFactory() {
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List files", "ls -la");
        assertEquals(0, step.index);
        assertEquals("List files", step.title);
        assertEquals("ls -la", step.command);
        assertNull(step.keys);
        assertNull(step.macroId);
        assertEquals("terminal", step.kind);
    }

    // ── Step factory: hid ───────────────────────────────────────────────

    @Test
    public void testHidStepFactory() {
        AgentPlan.Step step = AgentPlan.Step.hid(2, "Save file", "<CMD>s</CMD>");
        assertEquals(2, step.index);
        assertEquals("Save file", step.title);
        assertNull(step.command);
        assertEquals("<CMD>s</CMD>", step.keys);
        assertNull(step.macroId);
        assertEquals("hid", step.kind);
    }

    // ── Step factory: macro ─────────────────────────────────────────────

    @Test
    public void testMacroStepFactory() {
        AgentPlan.Step step = AgentPlan.Step.macro(1, "Run cleanup", "cleanup-macro");
        assertEquals(1, step.index);
        assertEquals("Run cleanup", step.title);
        assertNull(step.command);
        assertNull(step.keys);
        assertEquals("cleanup-macro", step.macroId);
        assertEquals("macro", step.kind);
    }

    // ── Step: explicit constructor ──────────────────────────────────────

    @Test
    public void testStepConstructorAllFields() {
        AgentPlan.Step step = new AgentPlan.Step(
                5, "Custom step", "cmd", "keys", "macroId", "custom_kind");
        assertEquals(5, step.index);
        assertEquals("Custom step", step.title);
        assertEquals("cmd", step.command);
        assertEquals("keys", step.keys);
        assertEquals("macroId", step.macroId);
        assertEquals("custom_kind", step.kind);
    }

    // ── Step: toString ──────────────────────────────────────────────────

    @Test
    public void testStepToStringContainsKindAndTitle() {
        AgentPlan.Step step = AgentPlan.Step.hid(3, "Press Enter", "<ENTER>");
        String str = step.toString();
        assertTrue(str.contains("hid"));
        assertTrue(str.contains("Press Enter"));
        assertTrue(str.contains("3"));
    }

    // ── AgentPlan: basic properties ─────────────────────────────────────

    @Test
    public void testEmptyPlan() {
        AgentPlan plan = new AgentPlan("Empty", Collections.emptyList());
        assertEquals("Empty", plan.summary);
        assertTrue(plan.isEmpty());
        assertEquals(0, plan.size());
    }

    @Test
    public void testNonEmptyPlan() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "Step 1", "ls"),
                AgentPlan.Step.terminal(1, "Step 2", "pwd"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        assertFalse(plan.isEmpty());
        assertEquals(2, plan.size());
        assertEquals("Demo", plan.summary);
    }

    @Test
    public void testStepsListIsUnmodifiable() {
        List<AgentPlan.Step> steps = new ArrayList<>();
        steps.add(AgentPlan.Step.terminal(0, "Step 1", "ls"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        try {
            plan.steps.add(AgentPlan.Step.terminal(1, "Step 2", "pwd"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected — steps list must be immutable
        }
    }

    @Test
    public void testStepsListIsDefensiveCopy() {
        List<AgentPlan.Step> original = new ArrayList<>();
        original.add(AgentPlan.Step.terminal(0, "Step 1", "ls"));
        AgentPlan plan = new AgentPlan("Demo", original);

        // Mutating original list must not affect plan
        original.add(AgentPlan.Step.terminal(1, "Step 2", "pwd"));
        assertEquals(1, plan.size());
    }

    // ── AgentPlan: truncateTo ───────────────────────────────────────────

    @Test
    public void testTruncateToLargerThanSizeReturnsSameInstance() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "a"),
                AgentPlan.Step.terminal(1, "B", "b"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(10);
        assertSame(plan, truncated); // same instance when no truncation needed
    }

    @Test
    public void testTruncateToExactSizeReturnsSameInstance() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "a"),
                AgentPlan.Step.terminal(1, "B", "b"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(2);
        assertSame(plan, truncated);
    }

    @Test
    public void testTruncateToSmallerSizeReturnsNewPlan() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "a"),
                AgentPlan.Step.terminal(1, "B", "b"),
                AgentPlan.Step.terminal(2, "C", "c"),
                AgentPlan.Step.terminal(3, "D", "d"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(2);
        assertNotSame(plan, truncated);
        assertEquals(2, truncated.size());
    }

    @Test
    public void testTruncateReindexesSteps() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "a"),
                AgentPlan.Step.terminal(1, "B", "b"),
                AgentPlan.Step.terminal(2, "C", "c"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(2);
        assertEquals(0, truncated.steps.get(0).index);
        assertEquals(1, truncated.steps.get(1).index);
        assertEquals("A", truncated.steps.get(0).title);
        assertEquals("B", truncated.steps.get(1).title);
    }

    @Test
    public void testTruncatePreservesStepKinds() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "T", "ls"),
                AgentPlan.Step.hid(1, "H", "<ENTER>"),
                AgentPlan.Step.macro(2, "M", "macro-id"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(2);
        assertEquals("terminal", truncated.steps.get(0).kind);
        assertEquals("hid", truncated.steps.get(1).kind);
        assertEquals("ls", truncated.steps.get(0).command);
        assertEquals("<ENTER>", truncated.steps.get(1).keys);
    }

    @Test
    public void testTruncateToZeroReturnsEmptyPlan() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "a"));
        AgentPlan plan = new AgentPlan("Demo", steps);

        AgentPlan truncated = plan.truncateTo(0);
        assertTrue(truncated.isEmpty());
    }

    // ── AgentPlan: toString ─────────────────────────────────────────────

    @Test
    public void testPlanToStringContainsSummaryAndSize() {
        AgentPlan plan = new AgentPlan("Check disk", Arrays.asList(
                AgentPlan.Step.terminal(0, "df", "df -h")));
        String str = plan.toString();
        assertTrue(str.contains("Check disk"));
        assertTrue(str.contains("1"));
    }

    // ── Mixed step kinds in single plan ─────────────────────────────────

    @Test
    public void testMixedStepKindsInPlan() {
        List<AgentPlan.Step> steps = Arrays.asList(
                AgentPlan.Step.hid(0, "Open terminal", "<CTRL><ALT>t</CTRL></ALT>"),
                AgentPlan.Step.terminal(1, "Check disk", "df -h"),
                AgentPlan.Step.macro(2, "Run cleanup", "cleanup-id"),
                AgentPlan.Step.hid(3, "Save", "<CMD>s</CMD>"));
        AgentPlan plan = new AgentPlan("Mixed plan", steps);

        assertEquals(4, plan.size());
        assertEquals("hid", plan.steps.get(0).kind);
        assertEquals("terminal", plan.steps.get(1).kind);
        assertEquals("macro", plan.steps.get(2).kind);
        assertEquals("hid", plan.steps.get(3).kind);
    }
}
