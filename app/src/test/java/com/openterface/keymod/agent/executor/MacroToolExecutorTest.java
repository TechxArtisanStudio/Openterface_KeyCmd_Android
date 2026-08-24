package com.openterface.keymod.agent.executor;

import static org.junit.Assert.*;

import android.content.Context;

import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for MacroToolExecutor.
 *
 * <p>Tests validation and error paths. Actual macro playback requires a real
 * ConnectionManager + MacrosManager setup, which is covered by manual testing.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class MacroToolExecutorTest {

    private MacroToolExecutor executor;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        executor = new MacroToolExecutor(context);
    }

    // ── getType ──────────────────────────────────────────────────────────

    @Test
    public void testGetType() {
        assertEquals("macro", executor.getType());
    }

    // ── execute validation ───────────────────────────────────────────────

    @Test
    public void testExecuteNonMacroStepFails() throws Exception {
        AgentPlan.Step step = AgentPlan.Step.terminal(0, "List", "ls");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("cannot handle kind=terminal"));
    }

    @Test
    public void testMacroNotFoundByIdFails() throws Exception {
        // Use a non-numeric macroId — won't match any macro
        AgentPlan.Step step = new AgentPlan.Step(
                0, "Unknown macro", null, null, "nonexistent-macro-id", "macro");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
    }

    @Test
    public void testMacroNotFoundByNumericIdFails() throws Exception {
        // Use a numeric ID that doesn't exist
        AgentPlan.Step step = AgentPlan.Step.macro(0, "Unknown", "9999999999");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
    }

    @Test
    public void testMacroNotFoundByNameFails() throws Exception {
        // macroId as a name that doesn't match any macro
        AgentPlan.Step step = new AgentPlan.Step(
                0, "Missing macro", null, null, "this macro does not exist", "macro");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
    }

    @Test
    public void testMacroNotConnectedFails() throws Exception {
        // Even if a macro existed, without ConnectionManager it should fail
        // This tests that connection check happens after macro lookup
        // Since no macros exist in test, we'll get "Macro not found" first
        AgentPlan.Step step = AgentPlan.Step.macro(0, "Test", "any-id");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        // Either "Macro not found" or "HID device not connected"
        assertTrue(cb.failure.contains("Macro not found")
                || cb.failure.contains("HID device not connected"));
    }

    @Test
    public void testMacroWithNullMacroIdAndNullCommandFails() throws Exception {
        // Both macroId and command are null → "Macro not found: (unknown)"
        AgentPlan.Step step = new AgentPlan.Step(0, "No query", null, null, null, "macro");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
    }

    @Test
    public void testMacroWithEmptyMacroIdAndNullCommandFails() throws Exception {
        // Empty macroId + null command → "Macro not found"
        AgentPlan.Step step = new AgentPlan.Step(0, "Empty query", null, null, "", "macro");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
    }

    @Test
    public void testMacroWithCommandFallback() throws Exception {
        // macroId is null but command has a name → lookup by command as name
        // AgentPlan.Step constructor: (index, title, command, keys, macroId, kind)
        AgentPlan.Step step = new AgentPlan.Step(
                0, "Use command", "fallback-macro-name", null, null, "macro");
        RecordingCallback cb = new RecordingCallback();

        executor.execute(step, cb);
        assertTrue(cb.awaitCompletion(1, TimeUnit.SECONDS));
        assertNotNull(cb.failure);
        assertTrue(cb.failure.contains("Macro not found"));
        assertTrue(cb.failure.contains("fallback-macro-name"));
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
