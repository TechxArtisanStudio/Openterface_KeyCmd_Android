package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;

/**
 * Tool executor interface — abstraction for executing Agent plan steps.
 *
 * <p>Concrete implementations:
 * <ul>
 *   <li>{@code TerminalToolExecutor} — SSH command execution</li>
 *   <li>{@code HidToolExecutor} — wireless keyboard control</li>
 *   <li>{@code MacroToolExecutor} — macro playback</li>
 *   <li>{@code CompositeToolExecutor} — routes steps to the above by kind</li>
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

        /**
         * Streaming output line from a running command.
         * Called on the executor thread for each line of stdout as it arrives
         * (before the command completes). Default is a no-op for backward
         * compatibility — implementers that support streaming should override.
         *
         * @param stepIndex the current step index
         * @param line      a single line of command output (no trailing newline)
         */
        default void onOutputLine(int stepIndex, @NonNull String line) {}
    }
}
