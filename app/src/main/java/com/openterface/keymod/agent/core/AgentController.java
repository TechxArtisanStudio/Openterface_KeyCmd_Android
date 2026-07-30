package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.llm.LlmRequest;
import com.openterface.keymod.agent.llm.LlmResponse;
import com.openterface.keymod.agent.llm.ProviderAdapter;
import com.openterface.keymod.agent.llm.ProviderAdapterFactory;
import com.openterface.terminal.CredentialProfile;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core Agent state machine and flow orchestrator.
 *
 * <p>Coordinates the full Agent lifecycle:
 * <pre>
 *   submit(userPrompt)
 *     → THINKING (LLM generates plan)
 *     → WAITING_APPROVE (plan ready for review)
 *     → approveAndRun()
 *     → EXECUTING (steps run via tool executor)
 *     → IDLE (done / cancelled / error)
 * </pre>
 *
 * <p>All listener callbacks are dispatched on the main thread.</p>
 *
 * <p>Thread safety: {@code state} and {@code currentPlan} are volatile so
 * UI-thread reads always see background-thread writes.</p>
 */
public final class AgentController {

    private static final String TAG = "AgentController";

    /** Timeout for waiting on a single step's execution callback */
    private static final long STEP_TIMEOUT_SECONDS = 300; // 5 minutes

    /** Agent-specific read timeout for LLM HTTP calls */
    private static final int AGENT_READ_TIMEOUT_MS = 60_000;

    /**
     * Provider name lookup — maps SharedPreferences index to ProviderAdapter name.
     * Delegates to {@link ProviderAdapterFactory#ADAPTER_NAMES} as single source of truth.
     */
    private static final String[] PROVIDER_NAMES = ProviderAdapterFactory.ADAPTER_NAMES;

    // ── State (volatile for cross-thread visibility) ─────────────────────

    private volatile AgentState state = AgentState.IDLE;
    @Nullable private volatile AgentPlan currentPlan;
    private int currentStepIndex = 0;
    @Nullable private Future<?> runningTask;

    // ── Dependencies ─────────────────────────────────────────────────────

    private final Context appContext;
    /** Original context — may be Activity, used for getActiveSshProfile() lookup. */
    private final Context originalContext;
    private final AgentPromptBuilder promptBuilder;
    private final AgentPlanParser planParser;
    private final AgentSession session;
    @Nullable private AgentToolExecutor toolExecutor;

    // ── Config ───────────────────────────────────────────────────────────

    private int maxSteps = 10;
    private int maxRetries = 3;
    private String customTerminalPrompt = "";
    private String customHidPrompt = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Cancel flag for LLM streaming ────────────────────────────────────

    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // ── Listener ─────────────────────────────────────────────────────────

    @Nullable private AgentListener listener;

    public AgentController(@NonNull Context context) {
        this.originalContext = context;
        this.appContext = context.getApplicationContext();
        this.promptBuilder = new AgentPromptBuilder(this.appContext);
        this.planParser = new AgentPlanParser();
        this.session = new AgentSession(this.appContext);

        // Load settings from SharedPreferences
        loadSettings();
    }

    /**
     * Load Agent settings from SharedPreferences.
     * Called on construction and can be called again to pick up changes.
     */
    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        maxSteps = prefs.getInt("agent_max_steps", 10);
        maxRetries = prefs.getInt("agent_max_retries", 3);
        customTerminalPrompt = prefs.getString("agent_prompt_terminal", "");
        customHidPrompt = prefs.getString("agent_prompt_hid", "");
    }

    // ── Public API ───────────────────────────────────────────────────────

    /** Set the listener for state changes and plan events */
    public void setListener(@Nullable AgentListener listener) {
        this.listener = listener;
    }

    /** Set the tool executor for running plan steps */
    public void setToolExecutor(@Nullable AgentToolExecutor executor) {
        this.toolExecutor = executor;
    }

    /** Get current state */
    @NonNull
    public AgentState getState() {
        return state;
    }

    /** Get the current plan (null if none generated yet) */
    @Nullable
    public AgentPlan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * Replace the current plan with an edited version (e.g. from EditPlanSheet).
     * Only allowed in {@link AgentState#WAITING_APPROVE} state.
     */
    public void updatePlan(@NonNull AgentPlan editedPlan) {
        if (state != AgentState.WAITING_APPROVE) {
            Log.w(TAG, "updatePlan() called in state " + state + ", ignoring");
            return;
        }
        currentPlan = editedPlan;
        Log.i(TAG, "Plan updated: " + editedPlan.steps.size() + " steps");
    }

    /** Get the prompt builder for configuring execution mode */
    @NonNull
    public AgentPromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    /** Get the session for message management */
    @NonNull
    public AgentSession getSession() {
        return session;
    }

    /** Set max retries for failed steps */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /** Set max steps for plan truncation */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * Shut down the background executor. Call from Fragment's onDestroy()
     * to prevent leaked threads.
     */
    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }

    /**
     * Submit a user prompt to generate an execution plan.
     * Transitions: IDLE → THINKING → WAITING_APPROVE (on success)
     */
    public void submit(@NonNull String userPrompt) {
        if (state != AgentState.IDLE && state != AgentState.ERROR) {
            Log.w(TAG, "submit() called in state " + state + ", ignoring");
            return;
        }

        if (userPrompt.trim().isEmpty()) {
            Log.w(TAG, "submit() called with empty prompt");
            return;
        }

        // Reset cancel flag for new submission
        cancelFlag.set(false);

        // Reload settings to pick up any changes from AgentSettingsBottomSheet
        loadSettings();

        // Record user message
        session.addUserMessage(userPrompt);

        // Transition to THINKING
        transitionTo(AgentState.THINKING);

        // Generate plan on background thread.
        // generatePlan() handles all exceptions internally via postError().
        runningTask = executor.submit(() -> generatePlan(userPrompt));
    }

    /**
     * Approve the current plan and start execution.
     * Transitions: WAITING_APPROVE → EXECUTING → IDLE
     */
    public void approveAndRun() {
        if (state != AgentState.WAITING_APPROVE || currentPlan == null) {
            Log.w(TAG, "approveAndRun() called in state " + state + ", ignoring");
            return;
        }

        transitionTo(AgentState.EXECUTING);
        currentStepIndex = 0;

        // Store Future so cancel() can interrupt execution
        runningTask = executor.submit(() -> executePlan());
    }

    /**
     * Cancel the current operation from any state.
     * Transitions: any → IDLE
     */
    public void cancel() {
        // Set cancel flag to stop any in-progress LLM streaming
        cancelFlag.set(true);

        if (runningTask != null && !runningTask.isDone()) {
            runningTask.cancel(true);
        }
        runningTask = null;
        if (toolExecutor != null) {
            toolExecutor.cancel();
        }
        currentPlan = null;
        currentStepIndex = 0;
        transitionTo(AgentState.IDLE);
    }

    /** Reset the controller to initial state */
    public void reset() {
        cancel();
        session.clear();
    }

    // ── Internal flow ────────────────────────────────────────────────────

    /**
     * Call LLM to generate an execution plan from user prompt.
     * Runs on background thread.
     *
     * <p>Prompt routing: if an SSH profile is active, uses terminal mode prompt.
     * Otherwise uses HID mode prompt (keyboard-only, no SSH).
     */
    private void generatePlan(@NonNull String userPrompt) {
        // Read AI settings from SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        String endpoint = prefs.getString("ai_endpoint", "https://api.openai.com/v1");
        String model = prefs.getString("ai_model", "gpt-4o-mini");
        int providerIdx = prefs.getInt("ai_provider", 0);
        String apiKey = prefs.getString("ai_api_key_" + providerIdx, "");

        // Build provider adapter
        String providerName = getProviderName(providerIdx);
        ProviderAdapter adapter = ProviderAdapterFactory.get(providerName);

        // Build HTTP client with Agent-appropriate timeout (60s read)
        LlmHttpClient httpClient = new LlmHttpClient(
                apiKey, endpoint, adapter,
                LlmHttpClient.DEFAULT_CONNECT_TIMEOUT_MS,
                AGENT_READ_TIMEOUT_MS);

        // ─ Prompt routing: detect SSH profile to choose execution mode ──
        CredentialProfile profile = getActiveSshProfile();
        if (profile != null) {
            promptBuilder.setExecutionMode("terminal");
            promptBuilder.setActiveProfile(profile);
            Log.i(TAG, "Prompt routing: terminal mode (SSH profile: "
                    + profile.getDisplayLabel() + ")");
        } else {
            promptBuilder.setExecutionMode("hid");
            promptBuilder.setActiveProfile(null);
            Log.i(TAG, "Prompt routing: HID mode (no SSH profile)");
        }

        // Build request with custom prompt support
        String customPrompt = getCustomPromptForMode(promptBuilder.getExecutionMode());
        LlmRequest request = promptBuilder.buildRequest(model, userPrompt, customPrompt);

        Log.i(TAG, "generatePlan: mode=" + promptBuilder.getExecutionMode()
                + ", model=" + model + ", messages=" + request.messages.size());

        try {
            // Call LLM synchronously (we're on background thread)
            Log.i(TAG, "generatePlan: calling LLM...");
            LlmResponse response = httpClient.chatSync(request);
            Log.i(TAG, "generatePlan: LLM response received, length=" + response.content.length());

            // Parse plan from response
            AgentPlan plan;
            try {
                plan = planParser.parseFromResponse(response);
            } catch (AgentPlanParser.PlanParseException e) {
                // Log the raw response for debugging
                Log.e(TAG, "Plan parse failed. Raw response: " + response.content);
                throw e;
            }

            // Sanitize: inject terminal-launch HID step if in HID mode
            if ("hid".equals(promptBuilder.getExecutionMode())) {
                String targetOS = getTargetOs();
                plan = PlanSanitizer.sanitizeForHid(plan, targetOS);
                if (plan.steps.size() > 1) {
                    Log.i(TAG, "sanitizeHIDPlan: injected terminal launch step (OS=" + targetOS + ")");
                }
            }

            // Truncate plan if it exceeds maxSteps
            if (plan.steps.size() > maxSteps) {
                plan = plan.truncateTo(maxSteps);
                final int truncatedTo = maxSteps;
                postToMain(() -> notifyPlanTruncated(truncatedTo));
            }

            // Store plan and record assistant message
            final AgentPlan finalPlan = plan;
            currentPlan = finalPlan;
            session.addAssistantMessage(finalPlan.summary);

            // Transition to WAITING_APPROVE on main thread
            postToMain(() -> {
                transitionTo(AgentState.WAITING_APPROVE);
                notifyPlanReady(finalPlan);
            });
        } catch (Exception e) {
            // Don't report error if cancelled
            if (cancelFlag.get()) {
                Log.d(TAG, "Plan generation cancelled");
                return;
            }
            Log.e(TAG, "Plan generation failed", e);
            postError(e.getMessage() != null ? e.getMessage() : "Failed to generate plan");
        }
    }

    /**
     * Execute plan steps sequentially with retry logic.
     * Runs on background thread.
     */
    private void executePlan() {
        if (currentPlan == null || currentPlan.isEmpty()) {
            postError("No plan to execute");
            return;
        }

        if (toolExecutor == null) {
            postToMain(() -> {
                notifyError("Tool executor not implemented");
                transitionTo(AgentState.IDLE);
            });
            return;
        }

        for (int i = currentStepIndex; i < currentPlan.steps.size(); i++) {
            currentStepIndex = i;
            final int stepIndex = i;
            AgentPlan.Step step = currentPlan.steps.get(i);

            // Report progress
            postToMain(() -> notifyProgress(stepIndex, currentPlan.steps.size()));

            // Execute with retry loop
            // HID mode: no retry (no output to inform LLM alternatives)
            boolean isHidMode = "hid".equals(promptBuilder.getExecutionMode());
            int effectiveMaxRetries = isHidMode ? 0 : maxRetries;

            boolean stepSucceeded = false;
            int localRetries = 0;

            while (!stepSucceeded && localRetries <= effectiveMaxRetries) {
                if (localRetries > 0) {
                    Log.w(TAG, "Retrying step " + stepIndex
                            + " (" + localRetries + "/" + effectiveMaxRetries + ")");
                    transitionTo(AgentState.RETRYING);
                    final int attempt = localRetries;
                    postToMain(() -> notifyRetry(attempt, effectiveMaxRetries));
                    transitionTo(AgentState.EXECUTING);
                }

                // Use CountDownLatch instead of busy-wait
                final CountDownLatch latch = new CountDownLatch(1);
                final String[] stepError = {null};

                toolExecutor.execute(step, new AgentToolExecutor.ExecutionCallback() {
                    @Override
                    public void onSuccess(@NonNull String output) {
                        // Build display lines based on step kind
                        List<String> lines = new ArrayList<>();
                        if ("terminal".equals(step.kind)) {
                            // Terminal: show "$ command" + output
                            lines.add("$ " + (step.command != null ? step.command : step.title));
                        } else {
                            // HID / Macro: show "🔑 title" + output
                            lines.add("🔑 " + step.title);
                        }
                        if (output != null && !output.isEmpty()) {
                            for (String line : output.split("\n")) {
                                lines.add(line);
                            }
                        }
                        session.addExecutionCliMessage(lines);
                        postToMain(() -> notifyStepOutput(stepIndex, lines));
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(@NonNull String error) {
                        List<String> lines = new ArrayList<>();
                        if ("terminal".equals(step.kind)) {
                            lines.add("$ " + (step.command != null ? step.command : step.title));
                        } else {
                            lines.add("🔑 " + step.title);
                        }
                        lines.add("Error: " + error);
                        session.addExecutionCliMessage(lines);
                        postToMain(() -> notifyStepOutput(stepIndex, lines));
                        stepError[0] = error;
                        latch.countDown();
                    }

                    @Override
                    public void onProgress(int idx, int total) {
                        postToMain(() -> notifyProgress(idx, total));
                    }
                });

                try {
                    boolean completed = latch.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!completed) {
                        stepError[0] = "Step timed out after " + STEP_TIMEOUT_SECONDS + "s";
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // cancelled
                }

                if (stepError[0] == null) {
                    stepSucceeded = true;
                } else {
                    localRetries++;
                    if (localRetries > effectiveMaxRetries) {
                        postError("Step " + (stepIndex + 1) + " failed after "
                                + effectiveMaxRetries + " retries: " + stepError[0]);
                        return;
                    }
                }
            }

            // Step interval: 0.5s delay between steps (spec requirement)
            if (i < currentPlan.steps.size() - 1 && !cancelFlag.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // All steps completed
        final boolean isHidModeFinal = "hid".equals(promptBuilder.getExecutionMode());
        final int totalSteps = currentPlan.steps.size();
        postToMain(() -> {
            if (isHidModeFinal) {
                // HID mode: no output to summarize → simple completion message
                session.addAssistantMessage("✅ Completed " + totalSteps + " step(s).");
                if (listener != null) {
                    listener.onStepOutput(totalSteps,
                            Collections.singletonList("✅ Completed " + totalSteps + " step(s)."));
                }
            }
            notifyExecutionComplete();
            transitionTo(AgentState.IDLE);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Get the custom prompt for the given execution mode.
     * Returns empty string if no custom prompt is configured.
     */
    @NonNull
    private String getCustomPromptForMode(@NonNull String mode) {
        switch (mode) {
            case "hid":
                return customHidPrompt != null ? customHidPrompt : "";
            case "terminal":
                return customTerminalPrompt != null ? customTerminalPrompt : "";
            default:
                return "";
        }
    }

    /**
     * Get the active SSH profile from MainActivity (Target Settings).
     * Returns null if context is not MainActivity or no profile is selected.
     */
    @Nullable
    private CredentialProfile getActiveSshProfile() {
        try {
            // Unwrap ContextWrapper chain to find the actual Activity
            android.content.Context ctx = originalContext;
            while (ctx instanceof android.content.ContextWrapper) {
                if (ctx instanceof com.openterface.keymod.MainActivity) {
                    return ((com.openterface.keymod.MainActivity) ctx).getActiveSshProfile();
                }
                ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
            }
            Log.w(TAG, "Context is not MainActivity: " + originalContext.getClass().getName());
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get active SSH profile", e);
            return null;
        }
    }

    /**
     * Get the target OS name. Uses active profile's targetOs if available,
     * otherwise falls back to agent_prefs.
     */
    @NonNull
    private String getTargetOs() {
        CredentialProfile profile = getActiveSshProfile();
        if (profile != null) {
            String os = profile.getTargetOs();
            if (os != null && !os.isEmpty()) return os;
        }
        SharedPreferences prefs = appContext.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE);
        String os = prefs.getString("agent_target_os", "linux");
        return os != null && !os.isEmpty() ? os : "linux";
    }

    /**
     * Transition to a new state and notify listener.
     * If called on the main thread, fires synchronously; otherwise posts.
     */
    private void transitionTo(@NonNull AgentState newState) {
        AgentState oldState = state;
        state = newState;
        Log.d(TAG, "State: " + oldState + " → " + newState);

        if (isMainThread()) {
            notifyStateChanged(newState);
        } else {
            postToMain(() -> notifyStateChanged(newState));
        }
    }

    /**
     * Post an error — transitions to ERROR state and notifies listener.
     * Ensures onStateChanged fires BEFORE onError.
     */
    private void postError(@NonNull String message) {
        postToMain(() -> {
            transitionTo(AgentState.ERROR);
            notifyError(message);
        });
    }

    /** Post a runnable to the main thread */
    private void postToMain(@NonNull Runnable action) {
        if (isMainThread()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    // ── Listener notification helpers ────────────────────────────────────

    private void notifyStateChanged(@NonNull AgentState newState) {
        if (listener != null) listener.onStateChanged(newState);
    }

    private void notifyPlanReady(@NonNull AgentPlan plan) {
        if (listener != null) listener.onPlanReady(plan);
    }

    private void notifyProgress(int currentStep, int totalSteps) {
        if (listener != null) listener.onExecutionProgress(currentStep, totalSteps);
    }

    private void notifyRetry(int attempt, int maxAttempts) {
        if (listener != null) listener.onRetry(attempt, maxAttempts);
    }

    private void notifyExecutionComplete() {
        if (listener != null) listener.onExecutionComplete();
    }

    private void notifyError(@NonNull String message) {
        if (listener != null) listener.onError(message);
    }

    private void notifyPlanTruncated(int maxSteps) {
        if (listener != null) listener.onPlanTruncated(maxSteps);
    }

    private void notifyStepOutput(int stepIndex, @NonNull List<String> lines) {
        if (listener != null) listener.onStepOutput(stepIndex, lines);
    }

    @NonNull
    private static String getProviderName(int index) {
        if (index >= 0 && index < PROVIDER_NAMES.length) {
            return PROVIDER_NAMES[index];
        }
        return "OpenAI";
    }

    // ── Listener interface ───────────────────────────────────────────────

    /** Callback interface for Agent state changes and events */
    public interface AgentListener {

        /** State machine transitioned to a new state */
        void onStateChanged(@NonNull AgentState state);

        /** Plan has been generated and is ready for review */
        void onPlanReady(@NonNull AgentPlan plan);

        /** Execution progress update */
        void onExecutionProgress(int currentStep, int totalSteps);

        /** A step failed and is being retried */
        void onRetry(int attempt, int maxAttempts);

        /** All steps completed successfully */
        void onExecutionComplete();

        /** An error occurred */
        void onError(@NonNull String message);

        /** Plan was truncated to fit maxSteps limit */
        default void onPlanTruncated(int maxSteps) {}

        /** A step produced output (for EXECUTION_CLI display) */
        default void onStepOutput(int stepIndex, @NonNull List<String> lines) {}
    }
}
