package com.openterface.keymod.agent.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request tracing manager for Agent operations.
 *
 * <p>Generates and manages session_id and trace_id for correlating
 * LLM calls and execution steps across the Agent lifecycle.
 *
 * <p>Session lifecycle:
 * <ul>
 *   <li>{@link #startSession()} — called on submit(), generates new session_id</li>
 *   <li>{@link #newTrace(String)} — called per LLM call, generates trace_id</li>
 *   <li>{@link #endSession(boolean, String)} — called on completion or error</li>
 * </ul>
 *
 * <p>Audit logs are written to {@code <filesDir>/agent_audit.log} in JSON Lines format.
 */
public final class TraceManager {

    private static final String TAG = "TraceManager";
    private static final String AUDIT_LOG_FILE = "agent_audit.log";
    private static final int MAX_LOG_SIZE_BYTES = 500_000; // 500KB rotation threshold

    private final Context appContext;

    @Nullable private String sessionId;
    private final AtomicInteger traceCounter = new AtomicInteger(0);
    private long sessionStartTime;

    // Current trace context
    @Nullable private String currentTraceId;
    @Nullable private String currentOperation;
    private long traceStartTime;

    public TraceManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ── Session Management ─────────────────────────────────────────────

    /**
     * Start a new Agent session. Call from submit().
     *
     * @return the new session_id (UUID)
     */
    @NonNull
    public String startSession() {
        sessionId = UUID.randomUUID().toString();
        sessionStartTime = System.currentTimeMillis();
        traceCounter.set(0);
        Log.i(TAG, "Session started: " + sessionId);
        return sessionId;
    }

    /**
     * End the current session.
     *
     * @param success whether the session completed successfully
     * @param reason optional reason for early termination (null if successful)
     */
    public void endSession(boolean success, @Nullable String reason) {
        if (sessionId == null) {
            return;
        }
        long duration = System.currentTimeMillis() - sessionStartTime;
        Log.i(TAG, "Session ended: " + sessionId
                + ", success=" + success
                + ", duration=" + duration + "ms"
                + (reason != null ? ", reason=" + reason : ""));

        writeAuditLog("session_end", sessionId, null, null,
                "success", String.valueOf(success),
                "duration_ms", String.valueOf(duration),
                "reason", reason != null ? reason : "completed");

        sessionId = null;
    }

    /**
     * Get the current session_id, or null if no session is active.
     */
    @Nullable
    public String getSessionId() {
        return sessionId;
    }

    // ── Trace Management ───────────────────────────────────────────────

    /**
     * Start a new trace for an operation (e.g., LLM call, step execution).
     *
     * @param operation operation name (e.g., "generate_plan", "execute_step", "retry_plan")
     * @return the new trace_id (UUID)
     */
    @NonNull
    public String newTrace(@NonNull String operation) {
        currentTraceId = UUID.randomUUID().toString();
        currentOperation = operation;
        traceStartTime = System.currentTimeMillis();
        traceCounter.incrementAndGet();
        return currentTraceId;
    }

    /**
     * End the current trace and record the result.
     *
     * @param success whether the operation succeeded
     * @param metadata optional key-value pairs for additional context
     */
    public void endTrace(boolean success, @Nullable String... metadata) {
        if (currentTraceId == null || sessionId == null) {
            return;
        }
        long duration = System.currentTimeMillis() - traceStartTime;
        Log.d(TAG, "Trace ended: " + currentTraceId
                + ", op=" + currentOperation
                + ", success=" + success
                + ", duration=" + duration + "ms");

        // Build audit log entry: 6 base fields (op, success, duration_ms as key-value pairs)
        // plus any caller-supplied metadata
        String[] fullMetadata = new String[metadata != null ? metadata.length + 6 : 6];
        fullMetadata[0] = "operation";
        fullMetadata[1] = currentOperation != null ? currentOperation : "unknown";
        fullMetadata[2] = "success";
        fullMetadata[3] = String.valueOf(success);
        fullMetadata[4] = "duration_ms";
        fullMetadata[5] = String.valueOf(duration);
        if (metadata != null && metadata.length >= 2) {
            System.arraycopy(metadata, 0, fullMetadata, 6, metadata.length);
        }

        writeAuditLog("trace_end", sessionId, currentTraceId, currentOperation, fullMetadata);

        currentTraceId = null;
        currentOperation = null;
    }

    /**
     * Get the current trace_id, or null if no trace is active.
     */
    @Nullable
    public String getCurrentTraceId() {
        return currentTraceId;
    }

    /**
     * Get the number of traces in the current session.
     */
    public int getTraceCount() {
        return traceCounter.get();
    }

    // ── Log Formatting ─────────────────────────────────────────────────

    /**
     * Format a log message with trace context.
     *
     * @param message the log message
     * @return formatted message with [session_id][trace_id] prefix
     */
    @NonNull
    public String formatLogMessage(@NonNull String message) {
        StringBuilder sb = new StringBuilder();
        if (sessionId != null) {
            sb.append("[session:").append(sessionId, 0, 8).append("]");
        }
        if (currentTraceId != null) {
            sb.append("[trace:").append(currentTraceId, 0, 8).append("]");
        }
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(message);
        return sb.toString();
    }

    // ── Audit Log Writing ──────────────────────────────────────────────

    /**
     * Write an audit log entry in JSON Lines format.
     */
    private void writeAuditLog(@NonNull String event,
                                @Nullable String sessionId,
                                @Nullable String traceId,
                                @Nullable String operation,
                                @NonNull String... metadata) {
        File logFile = new File(appContext.getFilesDir(), AUDIT_LOG_FILE);

        // Rotate if too large
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            logFile.delete();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        String timestamp = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"ts\":\"").append(timestamp).append("\"");
        json.append(",\"event\":\"").append(event).append("\"");
        if (sessionId != null) {
            json.append(",\"session_id\":\"").append(sessionId).append("\"");
        }
        if (traceId != null) {
            json.append(",\"trace_id\":\"").append(traceId).append("\"");
        }
        if (operation != null) {
            json.append(",\"operation\":\"").append(operation).append("\"");
        }
        // Add metadata as key-value pairs
        for (int i = 0; i < metadata.length - 1; i += 2) {
            String key = metadata[i];
            String value = metadata[i + 1];
            if (key != null && value != null) {
                json.append(",\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
            }
        }
        json.append("}\n");

        // Write to file (best-effort, don't fail on I/O error)
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(json.toString());
        } catch (IOException e) {
            Log.w(TAG, "Failed to write audit log", e);
        }
    }

    /**
     * Escape special characters for JSON string values.
     */
    @NonNull
    private static String escapeJson(@NonNull String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
