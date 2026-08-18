package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.agent.llm.ConversationManager;
import com.openterface.keymod.agent.ui.AgentMessage;
import com.openterface.terminal.CredentialProfile;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>This is a thin orchestration layer. Heavy logic is delegated to:
 * <ul>
 *   <li>{@link PlanGenerationUseCase} — LLM streaming, OS detection, plan parsing</li>
 *   <li>{@link PlanExecutionUseCase} — step execution, retry, timeout</li>
 *   <li>{@link SummaryUseCase} — LLM summarization with fallback</li>
 * </ul>
 *
 * <p>All listener callbacks are dispatched on the main thread.</p>
 *
 * <p>Thread safety: {@code state} and {@code currentPlan} are volatile so
 * UI-thread reads always see background-thread writes.</p>
 */
public final class AgentController {

    private static final String TAG = "AgentController";

    // ── State (volatile for cross-thread visibility) ─────────────────────

    private volatile AgentState state = AgentState.IDLE;
    @Nullable private volatile AgentPlan currentPlan;
    @Nullable private Future<?> runningTask;

    // ── OS Auto-detection ────────────────────────────────────────────────

    /** Detected target OS from SSH connection (null if not detected yet).
     *  Set by PlanGenerationUseCase callback, read by PlanExecutionUseCase. */
    @Nullable private volatile OsDetector.DetectedOS detectedOs;

    // ── Dependencies ─────────────────────────────────────────────────────

    private final Context appContext;
    /** Host environment — provides SSH profile, SshClient, and BLE service. */
    @NonNull private final AgentEnvironment environment;
    private final AgentPromptBuilder promptBuilder;
    private final AgentPlanParser planParser;
    private final AgentSession session;
    private final TraceManager traceManager;
    private final LlmClientFactory clientFactory;
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

    // ── UseCase handles (lazy, recreated per operation for fresh listener) ─

    @Nullable private PlanExecutionUseCase planExecUseCase;
    @Nullable private Thread summarizeThread;

    /** Session message count before the current execution round. Used by
     *  SummaryUseCase to scope result collection to the current round only. */
    private int executionStartSessionIndex = 0;

    // ── Multi-turn conversation (P1-7) ───────────────────────────────────

    /** ConversationManager for multi-turn context. Rebuilt when system prompt changes. */
    @Nullable private ConversationManager conversation;
    /** Tracks the system prompt used to build the current conversation. */
    @Nullable private String lastConversationSystemPrompt;
    /** Max tokens for conversation context trimming. */
    private static final int MAX_CONTEXT_TOKENS = 4000;

    public AgentController(@NonNull Context context, @NonNull AgentEnvironment environment) {
        this.appContext = context.getApplicationContext();
        this.environment = environment;
        this.promptBuilder = new AgentPromptBuilder(this.appContext);
        this.planParser = new AgentPlanParser();
        this.session = new AgentSession(this.appContext);
        this.traceManager = new TraceManager(this.appContext);
        this.clientFactory = new LlmClientFactory(this.appContext);

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
        if (planExecUseCase != null) {
            planExecUseCase.setToolExecutor(executor);
        }
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
        cancel();                 // cancels planExecUseCase and sets it to null
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
        detectedOs = null;

        // P0-4: Start new trace session
        traceManager.startSession();

        // Reload settings to pick up any changes from AgentSettingsBottomSheet
        loadSettings();

        // Record user message
        session.addUserMessage(userPrompt);

        // Transition to THINKING
        transitionTo(AgentState.THINKING);

        // Generate plan on background thread via PlanGenerationUseCase.
        runningTask = executor.submit(() -> {
            PlanGenerationUseCase planGen = createPlanGenerationUseCase();
            planGen.generatePlan(userPrompt, customTerminalPrompt, customHidPrompt);
        });
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
        String targetOs = getTargetOs();
        planExecUseCase = createPlanExecutionUseCase();
        executionStartSessionIndex = session.size();
        planExecUseCase.executePlan(currentPlan, targetOs);
    }

    /**
     * Re-execute the current plan from the beginning.
     * Only valid in {@link AgentState#WAITING_APPROVE} state (plan generated, not yet running).
     * Resets step index and retry counter, then starts execution.
     */
    public void reexecutePlan() {
        if (state != AgentState.WAITING_APPROVE || currentPlan == null) {
            Log.w(TAG, "reexecutePlan() called in state " + state + ", ignoring");
            return;
        }

        // Reset cancel flag for fresh execution
        cancelFlag.set(false);

        postToMain(() -> session.addAssistantMessage("🔄 Re-executing plan: " + currentPlan.summary));

        transitionTo(AgentState.EXECUTING);
        planExecUseCase = createPlanExecutionUseCase();
        planExecUseCase.reexecutePlan(getTargetOs());
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
        if (planExecUseCase != null) {
            planExecUseCase.shutdown();
            planExecUseCase = null;
        }
        if (summarizeThread != null && summarizeThread.isAlive()) {
            summarizeThread.interrupt();
            summarizeThread = null;
        }
        currentPlan = null;
        detectedOs = null;
        // End trace session on cancel
        traceManager.endSession(false, "cancelled");
        transitionTo(AgentState.IDLE);
    }

    /** Reset the controller to initial state */
    public void reset() {
        cancel();
        session.clear();
        // Clear multi-turn conversation history (P1-7)
        conversation = null;
        lastConversationSystemPrompt = null;
    }

    /**
     * Re-submit the last user prompt to regenerate the plan.
     * Called when the user taps the Retry button on an error message.
     * Finds the most recent USER message in the session and calls {@link #submit(String)}.
     */
    public void regeneratePlan() {
        if (state != AgentState.ERROR && state != AgentState.IDLE) {
            Log.w(TAG, "regeneratePlan() called in state " + state + ", ignoring");
            return;
        }

        // Find the last user message from session
        String lastUserPrompt = null;
        List<AgentMessage> msgs = session.getMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i).type == AgentMessage.Type.USER) {
                CharSequence text = msgs.get(i).text;
                lastUserPrompt = text != null ? text.toString() : null;
                break;
            }
        }

        if (lastUserPrompt == null || lastUserPrompt.trim().isEmpty()) {
            Log.w(TAG, "regeneratePlan(): no user prompt found");
            // Notify listener so UI can show feedback to user
            if (listener != null) {
                mainHandler.post(() -> listener.onError("No previous prompt to retry"));
            }
            return;
        }

        // Reset to IDLE if needed, then re-submit
        if (state == AgentState.ERROR) {
            state = AgentState.IDLE;
        }
        submit(lastUserPrompt);
    }

    /**
     * Public entry point for Fragment to trigger summarize phase.
     * Spawns a background thread so the LLM network call never blocks the UI.
     * Called after terminal card layout pass completes.
     */
    public void startSummarize() {
        if (cancelFlag.get()) return;
        summarizeThread = new Thread(() -> {
            SummaryUseCase summary = new SummaryUseCase(
                    session, promptBuilder, traceManager, clientFactory,
                    listener, cancelFlag, mainHandler, currentPlan,
                    executionStartSessionIndex,
                    () -> postToMain(() -> transitionTo(AgentState.IDLE)));
            summary.summarize();
        }, "AgentSummarize");
        summarizeThread.start();
    }

    // ── UseCase factories ────────────────────────────────────────────────

    /**
     * Get or create the ConversationManager for multi-turn context (P1-7).
     * Rebuilds if the system prompt has changed (e.g., execution mode switch).
     *
     * @param systemPrompt the current system prompt
     * @return ConversationManager instance with history preserved across submits
     */
    @NonNull
    private ConversationManager getOrCreateConversation(@NonNull String systemPrompt) {
        if (conversation == null || !systemPrompt.equals(lastConversationSystemPrompt)) {
            conversation = new ConversationManager(systemPrompt);
            conversation.setMaxContextTokens(MAX_CONTEXT_TOKENS);
            lastConversationSystemPrompt = systemPrompt;
            Log.i(TAG, "ConversationManager rebuilt (system prompt changed)");
        }
        return conversation;
    }

    /**
     * Create a PlanGenerationUseCase with current listener and config.
     * Recreated per submit() to capture the latest listener reference.
     */
    @NonNull
    private PlanGenerationUseCase createPlanGenerationUseCase() {
        return new PlanGenerationUseCase(
                appContext, environment, promptBuilder, planParser,
                session, traceManager, listener, cancelFlag, mainHandler,
                maxSteps,
                new PlanGenerationUseCase.Callback() {
                    @Override
                    public void onPlanReady(@NonNull AgentPlan plan) {
                        currentPlan = plan;
                        // NOTE: Raw LLM JSON response is stored as assistant message
                        // by PlanGenerationUseCase.onStreamFinished() — do NOT store
                        // a reformatted markdown summary here, it confuses the LLM.
                        postToMain(() -> {
                            transitionTo(AgentState.WAITING_APPROVE);
                            notifyPlanReady(plan);
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        postError(message);
                    }

                    @Override
                    public void onPlanTruncated(int max) {
                        notifyPlanTruncated(max);
                    }

                    @Override
                    public void onOsDetected(@NonNull OsDetector.DetectedOS os) {
                        detectedOs = os;
                    }

                    @Override
                    @NonNull
                    public ConversationManager onGetConversation(@NonNull String systemPrompt) {
                        return getOrCreateConversation(systemPrompt);
                    }
                });
    }

    /**
     * Create a PlanExecutionUseCase with current listener and config.
     * Recreated per approveAndRun() to capture the latest listener reference.
     */
    @NonNull
    private PlanExecutionUseCase createPlanExecutionUseCase() {
        PlanExecutionUseCase uc = new PlanExecutionUseCase(
                appContext, promptBuilder, planParser, session,
                traceManager, listener, cancelFlag, mainHandler,
                maxSteps, maxRetries,
                new PlanExecutionUseCase.Callback() {
                    @Override
                    public void onError(@NonNull String message) {
                        postError(message);
                    }

                    @Override
                    public void onTransitionToRetrying() {
                        transitionTo(AgentState.RETRYING);
                    }

                    @Override
                    public void onTransitionToExecuting() {
                        transitionTo(AgentState.EXECUTING);
                    }

                    @Override
                    public void onTransitionToIdle() {
                        transitionTo(AgentState.IDLE);
                    }

                    @Override
                    public void onExecutionCompleted(@NonNull List<String[]> results) {
                        // Add execution results as a USER message (system feedback).
                        // IMPORTANT: use "user" role, NOT "assistant" — execution results
                        // are system feedback, not the LLM's own output. Storing them as
                        // assistant messages confuses the LLM about its output format.
                        if (conversation != null && !results.isEmpty()) {
                            conversation.addUserMessage(
                                    "[System: execution results from previous plan]\n"
                                            + buildExecutionResultsContext(results));
                        }
                    }
                });
        uc.setToolExecutor(toolExecutor);
        return uc;
    }

    /**
     * Build a context string from execution results for the conversation history.
     * Includes commands, outputs (truncated), and success/failure status.
     */
    @NonNull
    private String buildExecutionResultsContext(@NonNull List<String[]> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Execution Results]\n");
        int successCount = 0;
        int failCount = 0;
        for (String[] result : results) {
            if (result.length >= 3) {
                String cmd = result[0];
                String output = result[1];
                String status = result[2];
                sb.append("- `").append(cmd).append("` → ");
                if ("success".equals(status)) {
                    successCount++;
                    // Truncate output for context window
                    if (output.length() > 150) {
                        sb.append(output.substring(0, 150)).append("...");
                    } else {
                        sb.append(output.isEmpty() ? "(no output)" : output);
                    }
                    sb.append(" ✅\n");
                } else {
                    failCount++;
                    sb.append("Error: ").append(output).append(" ❌\n");
                }
            }
        }
        sb.append("\nSummary: ").append(successCount).append(" succeeded, ")
                .append(failCount).append(" failed");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Get the target OS name. Priority:
     * 1. User's explicit OS selection from TargetSettingsSheet (agent_prefs)
     * 2. Auto-detected OS from SSH connection (if available and not UNKNOWN)
     * 3. Active profile's targetOs
     * 4. Default fallback ("linux")
     */
    @NonNull
    private String getTargetOs() {
        // Priority 1: User's explicit OS choice from TargetSettingsSheet.
        SharedPreferences prefs = appContext.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE);
        String userOs = prefs.getString("agent_target_os", "");
        if (userOs != null && !userOs.isEmpty()) {
            return userOs;
        }

        // Priority 2: Auto-detected OS from SSH (skip UNKNOWN — it means
        // detection failed, and we must not let it override the user's
        // profile-level setting below).
        if (detectedOs != null && detectedOs != OsDetector.DetectedOS.UNKNOWN) {
            return detectedOs.getCode();
        }

        // Priority 3: Profile-level OS
        CredentialProfile profile = environment.getActiveSshProfile();
        if (profile != null) {
            String os = profile.getTargetOs();
            if (os != null && !os.isEmpty()) return os;
        }

        // Priority 4: Default fallback
        return DEFAULT_TARGET_OS;
    }

    /**
     * Shared default target OS. All fallback paths across the Agent module
     * must use this constant so the default is consistent regardless of
     * which call path is taken.
     */
    public static final String DEFAULT_TARGET_OS = "linux";

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
     * Also adds an error message to the session so the UI can show a
     * retry-able error bubble (see AgentMessage.error).
     */
    private void postError(@NonNull String message) {
        // End trace session on error
        traceManager.endSession(false, message);

        // Post all state mutations to main thread for thread safety.
        // Check cancelFlag to prevent stale error from overwriting a cancel.
        postToMain(() -> {
            if (cancelFlag.get()) return;
            session.addMessage(AgentMessage.error(message, true));
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

    private void notifyPlanTruncated(int maxSteps) {
        if (listener != null) listener.onPlanTruncated(maxSteps);
    }

    private void notifyError(@NonNull String message) {
        if (listener != null) listener.onError(message);
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

        /** A step produced output (for EXECUTION_CLI display). isComplete=true when the step finished. */
        default void onStepOutput(int stepIndex, @NonNull List<String> lines,
                                   boolean isComplete, @Nullable Boolean success) {}

        /** A step is about to start executing — show Running state */
        default void onStepStart(int stepIndex, @NonNull String command) {}

        /** LLM streaming token received during THINKING phase */
        default void onToken(@NonNull String token) {}

        /** LLM streaming token during Summarize phase — append to Assistant message */
        default void onSummaryToken(@NonNull String token) {}

        /** Reset summary buffer state — called before chatSync fallback delivers full content */
        default void onSummaryReset() {}

        /** Summarize phase complete */
        default void onSummaryComplete() {}
    }
}
