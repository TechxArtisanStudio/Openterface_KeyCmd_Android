package com.openterface.keymod.agent.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for AgentController state machine.
 *
 * <p>Note: Since AgentController uses background threads and LLM calls,
 * these tests focus on the synchronous state transitions and initial state
 * verification. Integration tests with mock LLM are for Day 9.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class AgentControllerTest {

    @Test
    public void testInitialState() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        assertEquals(AgentState.IDLE, controller.getState());
        assertNull(controller.getCurrentPlan());
    }

    @Test
    public void testSubmitWithEmptyPromptIgnored() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.submit("");
        // Should remain IDLE because empty prompt is ignored
        assertEquals(AgentState.IDLE, controller.getState());
    }

    @Test
    public void testSubmitWithBlankPromptIgnored() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.submit("   ");
        assertEquals(AgentState.IDLE, controller.getState());
    }

    @Test
    public void testCancelFromIdleState() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.cancel();
        assertEquals(AgentState.IDLE, controller.getState());
    }

    @Test
    public void testResetClearsState() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.reset();
        assertEquals(AgentState.IDLE, controller.getState());
        assertNull(controller.getCurrentPlan());
    }

    @Test
    public void testSetMaxRetries() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.setMaxRetries(5);
        // No direct getter, but should not throw
    }

    @Test
    public void testPromptBuilderAccessible() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        assertNotNull(controller.getPromptBuilder());
        assertEquals("terminal", controller.getPromptBuilder().getExecutionMode());
    }

    @Test
    public void testSessionAccessible() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        assertNotNull(controller.getSession());
        assertEquals(0, controller.getSession().size());
    }

    @Test
    public void testSetListener() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.setListener(new AgentController.AgentListener() {
            @Override
            public void onStateChanged(AgentState state) {}
            @Override
            public void onPlanReady(AgentPlan plan) {}
            @Override
            public void onExecutionProgress(int currentStep, int totalSteps) {}
            @Override
            public void onRetry(int attempt, int maxAttempts) {}
            @Override
            public void onExecutionComplete() {}
            @Override
            public void onError(String message) {}
        });
        // Should not throw
    }

    @Test
    public void testSetToolExecutor() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        controller.setToolExecutor(new AgentToolExecutor() {
            @Override
            public String getType() { return "terminal"; }
            @Override
            public void execute(AgentPlan.Step step, ExecutionCallback callback) {}
            @Override
            public void cancel() {}
        });
        // Should not throw
    }

    @Test
    public void testApproveAndRunWithoutPlanIgnored() {
        AgentController controller = new AgentController(RuntimeEnvironment.getApplication());
        // Approve without a plan should be ignored
        controller.approveAndRun();
        assertEquals(AgentState.IDLE, controller.getState());
    }
}
