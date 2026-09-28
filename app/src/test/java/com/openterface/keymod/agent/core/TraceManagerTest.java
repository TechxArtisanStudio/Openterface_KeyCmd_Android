package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;

/**
 * Unit tests for {@link TraceManager}.
 */
@RunWith(RobolectricTestRunner.class)
public class TraceManagerTest {

    private TraceManager traceManager;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        traceManager = new TraceManager(ctx);
    }

    // ── Session Management ─────────────────────────────────────────────

    @Test
    public void testSessionIdNullBeforeStart() {
        assertNull(traceManager.getSessionId());
    }

    @Test
    public void testStartSessionReturnsUuid() {
        String sessionId = traceManager.startSession();
        assertNotNull(sessionId);
        // UUID format: 8-4-4-4-12 hex chars
        assertTrue("Session ID should be UUID format",
                sessionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    public void testStartSessionSetsSessionId() {
        String sessionId = traceManager.startSession();
        assertEquals(sessionId, traceManager.getSessionId());
    }

    @Test
    public void testStartSessionGeneratesUniqueIds() {
        String id1 = traceManager.startSession();
        String id2 = traceManager.startSession();
        assertNotEquals("Each session should have unique ID", id1, id2);
    }

    @Test
    public void testEndSessionClearsSessionId() {
        traceManager.startSession();
        assertNotNull(traceManager.getSessionId());
        traceManager.endSession(true, null);
        assertNull(traceManager.getSessionId());
    }

    @Test
    public void testEndSessionWithReason() {
        traceManager.startSession();
        traceManager.endSession(false, "test_error");
        assertNull(traceManager.getSessionId());
    }

    @Test
    public void testEndSessionWithoutStartIsNoOp() {
        // Should not throw
        traceManager.endSession(true, null);
        traceManager.endSession(false, "error");
        assertNull(traceManager.getSessionId());
    }

    // ── Trace Management ───────────────────────────────────────────────

    @Test
    public void testTraceIdNullBeforeNewTrace() {
        assertNull(traceManager.getCurrentTraceId());
    }

    @Test
    public void testNewTraceReturnsUuid() {
        traceManager.startSession();
        String traceId = traceManager.newTrace("test_op");
        assertNotNull(traceId);
        assertTrue("Trace ID should be UUID format",
                traceId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    public void testNewTraceSetsCurrentTraceId() {
        traceManager.startSession();
        String traceId = traceManager.newTrace("test_op");
        assertEquals(traceId, traceManager.getCurrentTraceId());
    }

    @Test
    public void testEndTraceClearsTraceId() {
        traceManager.startSession();
        traceManager.newTrace("test_op");
        assertNotNull(traceManager.getCurrentTraceId());
        traceManager.endTrace(true);
        assertNull(traceManager.getCurrentTraceId());
    }

    @Test
    public void testEndTraceWithoutActiveTraceIsNoOp() {
        // Should not throw
        traceManager.endTrace(true);
        traceManager.endTrace(false);
        assertNull(traceManager.getCurrentTraceId());
    }

    @Test
    public void testTraceCounterIncrements() {
        traceManager.startSession();
        assertEquals(0, traceManager.getTraceCount());
        traceManager.newTrace("op1");
        assertEquals(1, traceManager.getTraceCount());
        traceManager.endTrace(true);
        traceManager.newTrace("op2");
        assertEquals(2, traceManager.getTraceCount());
    }

    @Test
    public void testTraceCounterResetsOnNewSession() {
        traceManager.startSession();
        traceManager.newTrace("op1");
        traceManager.newTrace("op2");
        assertEquals(2, traceManager.getTraceCount());

        traceManager.startSession(); // new session
        assertEquals(0, traceManager.getTraceCount());
    }

    @Test
    public void testMultipleTracesInSession() {
        traceManager.startSession();
        String trace1 = traceManager.newTrace("op1");
        traceManager.endTrace(true);
        String trace2 = traceManager.newTrace("op2");
        traceManager.endTrace(false);

        assertNotEquals(trace1, trace2);
        assertEquals(2, traceManager.getTraceCount());
    }

    // ── Log Formatting ─────────────────────────────────────────────────

    @Test
    public void testFormatLogMessageNoSessionNoTrace() {
        String result = traceManager.formatLogMessage("test message");
        assertEquals("test message", result);
    }

    @Test
    public void testFormatLogMessageWithSessionOnly() {
        traceManager.startSession();
        String result = traceManager.formatLogMessage("test message");
        assertTrue("Should contain session prefix", result.startsWith("[session:"));
        assertTrue("Should contain the message", result.endsWith("test message"));
    }

    @Test
    public void testFormatLogMessageWithSessionAndTrace() {
        traceManager.startSession();
        traceManager.newTrace("test_op");
        String result = traceManager.formatLogMessage("test message");
        assertTrue("Should contain session prefix", result.contains("[session:"));
        assertTrue("Should contain trace prefix", result.contains("[trace:"));
        assertTrue("Should contain the message", result.endsWith("test message"));
    }

    @Test
    public void testFormatLogMessageSessionTracePrefixOrder() {
        traceManager.startSession();
        traceManager.newTrace("test_op");
        String result = traceManager.formatLogMessage("msg");
        // Format: [session:xxxxxxxx][trace:xxxxxxxx] msg
        int sessionIdx = result.indexOf("[session:");
        int traceIdx = result.indexOf("[trace:");
        assertTrue("Session prefix should come before trace prefix",
                sessionIdx < traceIdx);
    }

    @Test
    public void testFormatLogMessageTruncatesIdsTo8Chars() {
        String sessionId = traceManager.startSession();
        String result = traceManager.formatLogMessage("msg");
        // Session ID in log should be truncated to 8 chars
        String expectedPrefix = "[session:" + sessionId.substring(0, 8) + "]";
        assertTrue("Should contain truncated session ID",
                result.contains(expectedPrefix));
    }

    // ── End-to-End Session Lifecycle ───────────────────────────────────

    @Test
    public void testFullSessionLifecycle() {
        // Start session
        String sessionId = traceManager.startSession();
        assertNotNull(sessionId);
        assertEquals(sessionId, traceManager.getSessionId());
        assertEquals(0, traceManager.getTraceCount());

        // Generate plan trace
        String planTrace = traceManager.newTrace("generate_plan");
        assertNotNull(planTrace);
        assertEquals(1, traceManager.getTraceCount());
        traceManager.endTrace(true, "steps", "3");

        // Execute step traces
        String step1Trace = traceManager.newTrace("execute_step_0");
        assertNotNull(step1Trace);
        assertEquals(2, traceManager.getTraceCount());
        traceManager.endTrace(true);

        String step2Trace = traceManager.newTrace("execute_step_1");
        assertEquals(3, traceManager.getTraceCount());
        traceManager.endTrace(false, "error", "command failed");

        // End session
        traceManager.endSession(false, "step_failure");
        assertNull(traceManager.getSessionId());
        assertNull(traceManager.getCurrentTraceId());
    }

    @Test
    public void testEndTraceWithMetadata() {
        traceManager.startSession();
        traceManager.newTrace("llm_call");
        // Should not throw with metadata
        traceManager.endTrace(true, "model", "gpt-4", "tokens", "1500");
        assertNull(traceManager.getCurrentTraceId());
    }

    @Test
    public void testEndTraceWithNullMetadata() {
        traceManager.startSession();
        traceManager.newTrace("llm_call");
        // Should not throw with null metadata
        traceManager.endTrace(true, (String[]) null);
        assertNull(traceManager.getCurrentTraceId());
    }
}
