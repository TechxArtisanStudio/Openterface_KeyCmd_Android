package com.openterface.keymod.agent.executor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes plan steps to the appropriate tool executor based on {@code step.kind}.
 *
 * <p>Wraps multiple {@link AgentToolExecutor} implementations (terminal, hid, macro)
 * and dispatches each step to the executor whose {@link AgentToolExecutor#getType()}
 * matches the step's kind.</p>
 *
 * <p>This composite itself implements {@link AgentToolExecutor}, so it can be passed
 * directly to {@code AgentController.setToolExecutor()} without changes to the controller.</p>
 */
public final class CompositeToolExecutor implements AgentToolExecutor {

    private static final String TAG = "CompositeToolExecutor";

    private final Map<String, AgentToolExecutor> executors = new HashMap<>();

    /**
     * Register a tool executor for a specific step kind.
     * The executor's {@code getType()} must match the kind it handles.
     */
    public void register(@NonNull AgentToolExecutor executor) {
        executors.put(executor.getType(), executor);
        Log.i(TAG, "Registered executor: " + executor.getType()
                + " (" + executor.getClass().getSimpleName() + ")");
    }

    /**
     * Unregister a tool executor by type.
     */
    public void unregister(@NonNull String type) {
        executors.remove(type);
    }

    /**
     * Get the executor for a specific type.
     *
     * @return the executor, or null if no executor is registered for that type
     */
    @Nullable
    public AgentToolExecutor getExecutor(@NonNull String type) {
        return executors.get(type);
    }

    /**
     * Returns the number of registered executors.
     */
    public int size() {
        return executors.size();
    }

    @NonNull
    @Override
    public String getType() {
        return "composite";
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        String kind = step.kind;
        if (kind == null || kind.isEmpty()) {
            callback.onFailure("Step has no kind specified");
            return;
        }

        AgentToolExecutor executor = executors.get(kind);
        if (executor == null) {
            callback.onFailure("No executor registered for kind: " + kind
                    + ". Available: " + executors.keySet());
            return;
        }

        Log.i(TAG, "Dispatching step " + step.index + " (kind=" + kind
                + ", title=\"" + step.title + "\") to "
                + executor.getClass().getSimpleName());
        executor.execute(step, callback);
    }

    @Override
    public void cancel() {
        // Cancel all registered executors
        for (AgentToolExecutor executor : executors.values()) {
            try {
                executor.cancel();
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling executor " + executor.getType(), e);
            }
        }
    }
}
