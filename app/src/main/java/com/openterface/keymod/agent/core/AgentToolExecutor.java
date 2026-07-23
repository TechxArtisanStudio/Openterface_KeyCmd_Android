package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;

/**
 * Tool executor interface — abstraction for executing Agent plan steps.
 *
 * <p>Day 3 defines the interface only. Concrete implementations
 * arrive in Day 5-6:
 * <ul>
 *   <li>{@code TerminalToolExecutor} — SSH command execution</li>
 *   <li>{@code HidToolExecutor} — wireless keyboard control</li>
 *   <li>{@code MacroToolExecutor} — macro playback</li>
 * </ul>
 */
public interface AgentToolExecutor {

    /**
     * Executor type identifier.
     *
     * @return {@code "terminal"} | {@code "hid"} | {@code "macro"}
     */
    @NonNull
    String getType();

    /**
     * Execute a single plan step.
     *
     * @param step     the step to execute
     * @param callback result callback
     */
    void execute(@NonNull AgentPlan.Step step, @NonNull ExecutionCallback callback);

    /** Cancel the currently running step */
    void cancel();

    // ── Callback ─────────────────────────────────────────────────────────

    /** Callback for step execution results */
    interface ExecutionCallback {

        /** Step completed successfully */
        void onSuccess(@NonNull String output);

        /** Step failed */
        void onFailure(@NonNull String error);

        /** Execution progress update */
        void onProgress(int stepIndex, int totalSteps);
    }
}
