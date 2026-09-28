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

    // ═══════════════════════════════════════════════════════════════════
    // Day 6: Task Complexity Estimation Tests (estimateTaskComplexity)
    // ═══════════════════════════════════════════════════════════════════

    private static AgentPlan planWith(String... commands) {
        List<AgentPlan.Step> steps = new ArrayList<>();
        for (int i = 0; i < commands.length; i++) {
            steps.add(AgentPlan.Step.terminal(i + 1, "Step " + (i + 1), commands[i]));
        }
        return new AgentPlan("test", steps);
    }

    // ── Baseline: each step contributes at least +1 ───────────────────

    @Test
    public void testComplexityEmptyPlan() {
        AgentPlan plan = new AgentPlan("test", Collections.emptyList());
        assertEquals(0, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexitySimpleCommands() {
        // 3 simple commands → score = 3
        AgentPlan plan = planWith("ls -la", "pwd", "whoami");
        assertEquals(3, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexitySingleStep() {
        AgentPlan plan = planWith("uname -a");
        assertEquals(1, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Pipe operator adds +2 ─────────────────────────────────────────

    @Test
    public void testComplexityPipeCommand() {
        // 1 step with pipe + grep: 1 (base) + 2 (pipe) + 1 (grep) = 4
        AgentPlan plan = planWith("ps aux | grep java");
        assertEquals(4, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexityMultiplePipes() {
        // Multiple pipes in same step: base(1) + pipe(2) + find-search(1) = 4
        AgentPlan plan = planWith("find / -type f | head -20 | sort");
        assertEquals(4, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Redirection adds +1 ───────────────────────────────────────────

    @Test
    public void testComplexityRedirectCommand() {
        // 1 step with redirection: 1 (base) + 1 (redirect) = 2
        AgentPlan plan = planWith("ls > /tmp/output.txt");
        assertEquals(2, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Conditionals add +2 ───────────────────────────────────────────

    @Test
    public void testComplexityConditionalAnd() {
        // 1 step with &&: 1 (base) + 2 (conditional) = 3
        AgentPlan plan = planWith("test -f /tmp/x && echo exists");
        assertEquals(3, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexityConditionalOr() {
        AgentPlan plan = planWith("command1 || command2");
        assertEquals(3, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Search commands add +1 ────────────────────────────────────────

    @Test
    public void testComplexityFindCommand() {
        // 1 step with find: 1 (base) + 1 (search) = 2
        AgentPlan plan = planWith("find /tmp -name '*.log'");
        assertEquals(2, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexityGrepCommand() {
        // 1 step with grep: 1 (base) + 1 (search) = 2
        AgentPlan plan = planWith("grep error /var/log/syslog");
        assertEquals(2, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Complex real-world scenarios ──────────────────────────────────

    @Test
    public void testComplexitySystemAnalysis() {
        // Simulates a "analyze system" plan with multiple complex steps
        AgentPlan plan = planWith(
                "uname -a && cat /etc/os-release",      // 1 + 2 (&&) = 3
                "df -h | grep '^/dev/'",                 // 1 + 2 (|) + 1 (grep) = 4
                "free -h",                                // 1
                "ps aux --sort=-%mem | head -10",         // 1 + 2 (|) = 3
                "ss -tulpn"                               // 1
        );
        // Expected: 3 + 4 + 1 + 3 + 1 = 12
        assertEquals(12, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    @Test
    public void testComplexityCombinedFeatures() {
        // Pipe + redirect + conditional in one step
        AgentPlan plan = planWith("cat log | grep error > errors.txt && wc -l errors.txt");
        // 1 (base) + 2 (pipe) + 1 (redirect) + 2 (conditional) + 1 (grep) = 7
        assertEquals(7, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Ceiling at 15 ─────────────────────────────────────────────────

    @Test
    public void testComplexityCeilingAt15() {
        // Many highly complex steps should cap at 15
        List<AgentPlan.Step> steps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Each step: 1 base + 2 pipe + 2 conditional + 1 redirect + 1 search = 7
            steps.add(AgentPlan.Step.terminal(i + 1, "Step " + (i + 1),
                    "cmd1 | cmd2 && cmd3 > out | grep x"));
        }
        AgentPlan plan = new AgentPlan("test", steps);
        // 10 steps * 7 = 70 → capped at 15
        assertEquals(15, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Non-terminal steps (null command) ─────────────────────────────

    @Test
    public void testComplexityNonTerminalStepCounted() {
        // HID / macro steps with null command still count as +1
        List<AgentPlan.Step> steps = Arrays.asList(
                new AgentPlan.Step(1, "Type keys", null, "<CMD>c</CMD>", null, "hid"),
                AgentPlan.Step.terminal(2, "Run cmd", "ls")
        );
        AgentPlan plan = new AgentPlan("test", steps);
        // 1 (hid, null cmd) + 1 (terminal, simple) = 2
        assertEquals(2, PlanGenerationUseCase.estimateTaskComplexity(plan));
    }

    // ── Dynamic maxSteps behavior ─────────────────────────────────────

    @Test
    public void testDynamicMaxStepsSimplePlanWithinLimit() {
        // Simple plan (score=3) with maxSteps=10 → effective=10 (not raised)
        // effectiveMaxSteps = min(max(10, 3*2), 20) = min(max(10, 6), 20) = 10
        // So a plan with 5 steps and maxSteps=10 should NOT be truncated
        AgentPlan plan = planWith("ls", "pwd", "whoami", "uname", "date");
        int complexity = PlanGenerationUseCase.estimateTaskComplexity(plan);
        assertEquals(5, complexity);
        int effectiveMaxSteps = Math.min(Math.max(10, complexity * 2), 20);
        assertEquals(10, effectiveMaxSteps);
        assertTrue(plan.steps.size() <= effectiveMaxSteps);
    }

    @Test
    public void testDynamicMaxStepsComplexPlanGetsMoreSteps() {
        // Complex plan (score=11) with maxSteps=10 → effective=20 (raised)
        // effectiveMaxSteps = min(max(10, 11*2), 20) = min(max(10, 22), 20) = 20
        AgentPlan plan = planWith(
                "uname -a && cat /etc/os-release",
                "df -h | grep '^/dev/'",
                "free -h",
                "ps aux --sort=-%mem | head -10",
                "ss -tulpn",
                "netstat -an | grep ESTABLISHED",
                "top -bn1 | head -20",
                "journalctl -xe | tail -20",
                "cat /var/log/syslog | grep error | head -5",
                "lsof -i | head -10",
                "ifconfig | grep inet",
                "uptime"
        );
        int complexity = PlanGenerationUseCase.estimateTaskComplexity(plan);
        assertTrue("Complex plan should have high score: " + complexity, complexity >= 10);
        int effectiveMaxSteps = Math.min(Math.max(10, complexity * 2), 20);
        assertEquals(20, effectiveMaxSteps);
    }
}
