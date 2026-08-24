package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

/**
 * Unit tests for PlanSanitizer.
 */
@RunWith(RobolectricTestRunner.class)
public class PlanSanitizerTest {

    // ── shouldInject ────────────────────────────────────────────────────

    @Test
    public void testShouldInjectWhenTerminalWithoutHidPrefix() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.terminal(0, "Check", "df -h")));
        assertTrue(PlanSanitizer.shouldInject(plan));
    }

    @Test
    public void testShouldNotInjectWhenHidBeforeTerminal() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.hid(0, "Open", "<CTRL><ALT>t<DELAY3S>"),
                AgentPlan.Step.terminal(1, "Check", "df -h")));
        assertFalse(PlanSanitizer.shouldInject(plan));
    }

    @Test
    public void testShouldNotInjectWhenOnlyHidSteps() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.hid(0, "Type", "hello<ENTER>")));
        assertFalse(PlanSanitizer.shouldInject(plan));
    }

    @Test
    public void testShouldNotInjectWhenEmptyPlan() {
        AgentPlan plan = new AgentPlan("Test", java.util.Collections.emptyList());
        assertFalse(PlanSanitizer.shouldInject(plan));
    }

    @Test
    public void testShouldInjectWhenTerminalAfterMacro() {
        // macro before terminal, no hid → should inject
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.macro(0, "Run", "cleanup"),
                AgentPlan.Step.terminal(1, "Check", "df -h")));
        assertTrue(PlanSanitizer.shouldInject(plan));
    }

    // ── sanitizeForHid ─────────────────────────────────────────────────

    @Test
    public void testSanitizeInjectsStepForLinux() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.terminal(0, "Check", "df -h")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "linux");

        assertEquals(2, sanitized.steps.size());
        assertEquals("hid", sanitized.steps.get(0).kind);
        assertEquals("<CTRL><ALT>t<DELAY3S>", sanitized.steps.get(0).keys);
        assertEquals("terminal", sanitized.steps.get(1).kind);
        assertEquals(1, sanitized.steps.get(1).index); // re-indexed
        assertTrue(sanitized.summary.contains("auto-injected"));
    }

    @Test
    public void testSanitizeInjectsStepForMacOS() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.terminal(0, "Check", "df -h")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "macos");

        assertEquals("hid", sanitized.steps.get(0).kind);
        assertTrue(sanitized.steps.get(0).keys.contains("<CMD><SPACE></CMD>"));
    }

    @Test
    public void testSanitizeInjectsStepForWindows() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.terminal(0, "Check", "dir")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "windows");

        assertEquals("hid", sanitized.steps.get(0).kind);
        assertTrue(sanitized.steps.get(0).keys.contains("<WIN>"));
    }

    @Test
    public void testSanitizeReturnsSamePlanWhenHidAlreadyPresent() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.hid(0, "Open", "<CTRL><ALT>t<DELAY3S>"),
                AgentPlan.Step.terminal(1, "Check", "df -h")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "linux");

        assertSame(plan, sanitized); // no modification
    }

    @Test
    public void testSanitizeReturnsSamePlanForHidOnlySteps() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.hid(0, "Type", "hello<ENTER>")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "linux");
        assertSame(plan, sanitized);
    }

    @Test
    public void testSanitizeReindexesAllSteps() {
        AgentPlan plan = new AgentPlan("Test", Arrays.asList(
                AgentPlan.Step.terminal(0, "A", "cmd1"),
                AgentPlan.Step.terminal(1, "B", "cmd2"),
                AgentPlan.Step.terminal(2, "C", "cmd3")));

        AgentPlan sanitized = PlanSanitizer.sanitizeForHid(plan, "linux");

        assertEquals(4, sanitized.steps.size());
        assertEquals(0, sanitized.steps.get(0).index); // injected hid
        assertEquals(1, sanitized.steps.get(1).index); // was 0
        assertEquals(2, sanitized.steps.get(2).index); // was 1
        assertEquals(3, sanitized.steps.get(3).index); // was 2
    }

    // ── getLaunchPayload ───────────────────────────────────────────────

    @Test
    public void testGetLaunchPayloadLinux() {
        assertEquals("<CTRL><ALT>t<DELAY3S>", PlanSanitizer.getLaunchPayload("linux"));
    }

    @Test
    public void testGetLaunchPayloadMacOS() {
        String payload = PlanSanitizer.getLaunchPayload("macos");
        assertTrue(payload.contains("<CMD><SPACE></CMD>"));
        assertTrue(payload.contains("terminal<ENTER>"));
    }

    @Test
    public void testGetLaunchPayloadWindows() {
        String payload = PlanSanitizer.getLaunchPayload("windows");
        assertTrue(payload.contains("<WIN>"));
        assertTrue(payload.contains("cmd<ENTER>"));
    }

    @Test
    public void testGetLaunchPayloadCaseInsensitive() {
        assertEquals("<CTRL><ALT>t<DELAY3S>", PlanSanitizer.getLaunchPayload("Linux"));
        assertEquals("<CTRL><ALT>t<DELAY3S>", PlanSanitizer.getLaunchPayload("LINUX"));
    }

    @Test
    public void testGetLaunchPayloadDefaultToMacOS() {
        // Unknown OS falls back to macOS
        String payload = PlanSanitizer.getLaunchPayload("unknown");
        assertTrue(payload.contains("<CMD><SPACE></CMD>"));
    }

    // ── getLaunchTitle ──────────────────────────────────────────────────

    @Test
    public void testGetLaunchTitleLinux() {
        assertEquals("Open Terminal via Ctrl+Alt+T", PlanSanitizer.getLaunchTitle("linux"));
    }

    @Test
    public void testGetLaunchTitleMacOS() {
        assertEquals("Open Terminal via Spotlight", PlanSanitizer.getLaunchTitle("macos"));
    }

    @Test
    public void testGetLaunchTitleWindows() {
        assertEquals("Open Command Prompt via Win+R", PlanSanitizer.getLaunchTitle("windows"));
    }
}
