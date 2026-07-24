package com.openterface.keymod.agent.executor;

import static org.junit.Assert.*;

import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for TerminalToolExecutor.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalToolExecutorTest {

    private TerminalToolExecutor executor;

    @Before
    public void setUp() {
        executor = new TerminalToolExecutor();
    }

    // ── getType ──────────────────────────────────────────────────────────

    @Test
    public void testGetType() {
        assertEquals("terminal", executor.getType());
    }

    // ── execute validation ───────────────────────────────────────────────

    @Test
    public void testExecuteNonTerminalStepFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.hid(0, "Save", "<CMD>s</CMD>");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("cannot handle kind=hid"));
    }

    @Test
    public void testExecuteNullCommandFails() throws Exception {
        AgentPlan.Step step = new AgentPlan.Step(0, "No command", null, null, null, "terminal");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("no command"));
    }

    @Test
    public void testExecuteEmptyCommandFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "Empty", "   ");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("no command"));
    }

    @Test
    public void testExecuteSshNotConnectedFails() throws Exception {
        // No SSH client set → should fail with "not connected"
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("SSH not connected"));
    }

    // ── truncateOutput ───────────────────────────────────────────────────

    @Test
    public void testTruncateShortOutputUnchanged() {
        String output = "hello\nworld";
        assertEquals(output, executor.truncateOutput(output));
    }

    @Test
    public void testTruncateEmptyOutputUnchanged() {
        assertEquals("", executor.truncateOutput(""));
    }

    @Test
    public void testTruncateLongOutputByChars() {
        // Build a 3000-char output
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb.append('x');
        }
        String output = sb.toString();
        String truncated = executor.truncateOutput(output);

        assertTrue(truncated.length() < output.length());
        assertTrue(truncated.contains("chars truncated"));
        // Result should start with 2000 chars of output
        assertTrue(truncated.startsWith("xxxxxxxxxx"));
    }

    @Test
    public void testTruncateManyLines() {
        // Build 100 short lines (each ~5 chars, total ~500 chars < 2000)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append("\n");
            sb.append("line").append(i);
        }
        String output = sb.toString();
        String truncated = executor.truncateOutput(output);

        assertTrue(truncated.contains("more lines truncated"));
        // Count output lines — should be 50 + truncation notice = 51
        String[] lines = truncated.split("\n", -1);
        // 50 content lines + 1 truncation notice
        assertEquals(51, lines.length);
    }

    @Test
    public void testTruncateExactlyAtBoundary() {
        // 2000 chars exactly → no truncation
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append('a');
        }
        String output = sb.toString();
        assertEquals(output, executor.truncateOutput(output));
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Test
    public void testCancelWhenNothingRunning() {
        // Should not crash
        executor.cancel();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Simple recording callback for synchronous test assertions. */
    private static class RecordingCallback implements AgentToolExecutor.ExecutionCallback {
        final CountDownLatch latch = new CountDownLatch(1);
        String success;
        String failure;

        @Override
        public void onSuccess(String output) {
            success = output;
            latch.countDown();
        }

        @Override
        public void onFailure(String error) {
            failure = error;
            latch.countDown();
        }

        @Override
        public void onProgress(int stepIndex, int totalSteps) {
            // ignored
        }

        boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
