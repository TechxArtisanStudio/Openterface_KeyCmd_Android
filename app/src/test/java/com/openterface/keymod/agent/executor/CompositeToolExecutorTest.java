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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for CompositeToolExecutor.
 */
@RunWith(RobolectricTestRunner.class)
public class CompositeToolExecutorTest {

    private CompositeToolExecutor composite;

    @Before
    public void setUp() {
        composite = new CompositeToolExecutor();
    }

    // ── getType ──────────────────────────────────────────────────────────

    @Test
    public void testGetType() {
        assertEquals("composite", composite.getType());
    }

    // ── register ─────────────────────────────────────────────────────────

    @Test
    public void testRegisterAddsExecutor() {
        assertEquals(0, composite.size());
        composite.register(new StubExecutor("terminal"));
        assertEquals(1, composite.size());
        composite.register(new StubExecutor("hid"));
        assertEquals(2, composite.size());
    }

    @Test
    public void testGetExecutorAfterRegister() {
        StubExecutor terminal = new StubExecutor("terminal");
        composite.register(terminal);
        assertSame(terminal, composite.getExecutor("terminal"));
    }

    @Test
    public void testGetExecutorUnregisteredReturnsNull() {
        assertNull(composite.getExecutor("nonexistent"));
    }

    @Test
    public void testUnregisterRemovesExecutor() {
        composite.register(new StubExecutor("terminal"));
        assertEquals(1, composite.size());
        composite.unregister("terminal");
        assertEquals(0, composite.size());
        assertNull(composite.getExecutor("terminal"));
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    @Test
    public void testDispatchTerminalToTerminalExecutor() throws Exception {
        StubExecutor terminal = new StubExecutor("terminal");
        composite.register(terminal);

        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(terminal.lastStep);
        assertEquals("ls", terminal.lastStep.command);
        assertNotNull(cb.success);
    }

    @Test
    public void testDispatchHidToHidExecutor() throws Exception {
        StubExecutor hid = new StubExecutor("hid");
        composite.register(hid);

        AgentPlan.Step step = AgentPlan.Step.hid(0, "Save", "<CMD>s</CMD>");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertSame(step, hid.lastStep);
        assertNotNull(cb.success);
    }

    @Test
    public void testDispatchMacroToMacroExecutor() throws Exception {
        StubExecutor macro = new StubExecutor("macro");
        composite.register(macro);

        AgentPlan.Step step = AgentPlan.Step.macro(0, "Run", "test-macro");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertSame(step, macro.lastStep);
        assertNotNull(cb.success);
    }

    @Test
    public void testDispatchToCorrectExecutorAmongMultiple() throws Exception {
        StubExecutor terminal = new StubExecutor("terminal");
        StubExecutor hid = new StubExecutor("hid");
        StubExecutor macro = new StubExecutor("macro");
        composite.register(terminal);
        composite.register(hid);
        composite.register(macro);

        // Send a HID step — only HID executor should receive it
        AgentPlan.Step step = AgentPlan.Step.hid(0, "Copy", "<CMD>c</CMD>");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));

        assertNull(terminal.lastStep);
        assertSame(step, hid.lastStep);
        assertNull(macro.lastStep);
    }

    @Test
    public void testDispatchUnknownKindFails() throws Exception {
        composite.register(new StubExecutor("terminal"));

        AgentPlan.Step step = new AgentPlan.Step(0, "Unknown", null, null, null, "unknown_kind");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("No executor registered for kind: unknown_kind"));
    }

    @Test
    public void testDispatchNullKindFails() throws Exception {
        AgentPlan.Step step = new AgentPlan.Step(0, "Null kind", null, null, null, "");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("no kind"));
    }

    @Test
    public void testDispatchWithNoRegisteredExecutorsFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("No executor registered"));
    }

    // ── cancel ───────────────────────────────────────────────────────────

    @Test
    public void testCancelAllExecutors() {
        StubExecutor terminal = new StubExecutor("terminal");
        StubExecutor hid = new StubExecutor("hid");
        composite.register(terminal);
        composite.register(hid);

        composite.cancel();
        assertTrue(terminal.cancelled);
        assertTrue(hid.cancelled);
    }

    @Test
    public void testCancelWithNoExecutors() {
        // Should not throw
        composite.cancel();
    }

    // ── failure propagation ──────────────────────────────────────────────

    @Test
    public void testExecutorFailurePropagated() throws Exception {
        StubExecutor terminal = new StubExecutor("terminal");
        terminal.shouldFail = true;
        composite.register(terminal);

        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("simulated failure"));
    }

    // ── progress propagation ─────────────────────────────────────────────

    @Test
    public void testProgressCallbackPropagated() throws Exception {
        StubExecutor terminal = new StubExecutor("terminal");
        terminal.reportProgress = true;
        composite.register(terminal);

        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertTrue(cb.progressReported);
        assertEquals(0, cb.progressStepIndex);
        assertEquals(10, cb.progressTotalSteps);
    }

    // ── re-register overwrites ───────────────────────────────────────────

    @Test
    public void testReRegisterSameTypeOverwrites() throws Exception {
        StubExecutor first = new StubExecutor("terminal");
        StubExecutor second = new StubExecutor("terminal");
        composite.register(first);
        composite.register(second);

        // Size should still be 1 (same key)
        assertEquals(1, composite.size());
        // Should return the second one
        assertSame(second, composite.getExecutor("terminal"));

        // Dispatch should go to second executor
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();
        composite.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));

        assertNull(first.lastStep);
        assertSame(step, second.lastStep);
    }

    // ── cancel exception isolation ───────────────────────────────────────

    @Test
    public void testCancelIsolatesExceptions() {
        StubExecutor good = new StubExecutor("terminal") {
            @Override
            public void cancel() {
                throw new RuntimeException("boom");
            }
        };
        StubExecutor hid = new StubExecutor("hid");
        composite.register(good);
        composite.register(hid);

        // Should not throw — cancel continues to next executor
        composite.cancel();
        assertTrue(hid.cancelled);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Stub executor that records the last step it received. */
    private static class StubExecutor implements AgentToolExecutor {
        final String type;
        AgentPlan.Step lastStep;
        boolean cancelled;
        boolean shouldFail;
        boolean reportProgress;

        StubExecutor(String type) {
            this.type = type;
        }

        @Override
        public String getType() { return type; }

        @Override
        public void execute(AgentPlan.Step step, ExecutionCallback callback) {
            lastStep = step;
            if (reportProgress) {
                callback.onProgress(step.index, 10);
            }
            if (shouldFail) {
                callback.onFailure("simulated failure");
            } else {
                callback.onSuccess("stub output for " + type);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    /** Recording callback for synchronous assertions. */
    private static class RecordingCallback implements AgentToolExecutor.ExecutionCallback {
        final CountDownLatch latch = new CountDownLatch(1);
        String success;
        String failure;
        boolean progressReported;
        int progressStepIndex;
        int progressTotalSteps;

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
            progressReported = true;
            progressStepIndex = stepIndex;
            progressTotalSteps = totalSteps;
        }

        boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
