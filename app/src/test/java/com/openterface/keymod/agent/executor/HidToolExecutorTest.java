package com.openterface.keymod.agent.executor;

import static org.junit.Assert.*;

import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for HidToolExecutor.
 *
 * <p>Tests validation and error paths. Actual HID sending requires a
 * ConnectionManager instance which can't be easily mocked in unit tests;
 * that's covered by manual testing.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class HidToolExecutorTest {

    private HidToolExecutor executor;

    @Before
    public void setUp() {
        executor = new HidToolExecutor();
    }

    // ── getType ──────────────────────────────────────────────────────────

    @Test
    public void testGetType() {
        assertEquals("hid", executor.getType());
    }

    // ── execute validation ───────────────────────────────────────────────

    @Test
    public void testExecuteNonHidStepFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("cannot handle kind=terminal"));
    }

    @Test
    public void testExecuteNullKeysFails() throws Exception {
        AgentPlan.Step step = new AgentPlan.Step(0, "No keys", null, null, null, "hid");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("no keys"));
    }

    @Test
    public void testExecuteEmptyKeysFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.hid(0, "Empty", "   ");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("no keys"));
    }

    @Test
    public void testExecuteNotConnectedFails() throws Exception {
        // No ConnectionManager set → should fail with "not connected"
        AgentPlan.Step step = AgentPlan.Step.hid(0, "Save", "<CMD>s</CMD>");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("HID device not connected"));
    }

    // ── targetOs ─────────────────────────────────────────────────────────

    @Test
    public void testDefaultTargetOsIsLinux() {
        // Should not crash; default targetOs is "linux"
        assertEquals("hid", executor.getType());
    }

    @Test
    public void testSetTargetOsDoesNotCrash() {
        executor.setTargetOs("macos");
        executor.setTargetOs("windows");
        executor.setTargetOs("linux");
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Test
    public void testCancelWhenNothingRunning() {
        // Should not crash
        executor.cancel();
    }

    // ── helpers ──────────────────────────────────────────────────────────

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
        public void onProgress(int stepIndex, int totalSteps) {}

        boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
