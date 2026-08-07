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
import com.openterface.keymod.agent.llm.LlmResult;
import com.openterface.keymod.agent.llm.ProviderAdapter;
import com.openterface.keymod.agent.llm.ProviderAdapterFactory;
import com.openterface.keymod.agent.settings.AIConfigProvider;
import com.openterface.keymod.agent.ui.AgentMessage;
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
    private static final long STEP_TIMEOUT_SECONDS = 30; // 30 seconds per step

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

    // ── Internal flow ────────────────────────────────────────────────────

    /**
     * Call LLM to generate an execution plan from user prompt.
     * Runs on background thread.
     *
     * <p>Prompt routing: if an SSH profile is active, uses terminal mode prompt.
     * Otherwise uses HID mode prompt (keyboard-only, no SSH).
     */
    private void generatePlan(@NonNull String userPrompt) {
        // Read AI settings from unified config provider
        AIConfigProvider config = AIConfigProvider.getInstance(appContext);
        String endpoint = config.getEndpoint();
        String model = config.getModel();
        String apiKey = config.getApiKey();
        ProviderAdapter adapter = config.getAdapter();

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
            // Call LLM with streaming to show tokens in real-time.
            // chatStream() blocks until the stream completes.
            Log.i(TAG, "generatePlan: calling LLM (streaming)...");
            final StringBuilder streamingContent = new StringBuilder();
            final LlmResponse[] streamResult = {null};

            httpClient.chatStream(request, adapter, new LlmResult() {
                private int chunkCount = 0;

                @Override
                public void onChunk(@NonNull LlmResponse chunk) {
                    chunkCount++;
                    Log.d(TAG, "onChunk #" + chunkCount
                            + ": contentLen=" + chunk.content.length()
                            + ", finishReason=" + chunk.finishReason
                            + ", content=\"" + chunk.content + "\"");
                    if (!chunk.content.isEmpty()) {
                        streamingContent.append(chunk.content);
                        postToMain(() -> notifyToken(chunk.content));
                    }
                }

                @Override
                public void onComplete(@NonNull LlmResponse fullResponse) {
                    streamResult[0] = fullResponse;
                    Log.i(TAG, "generatePlan: stream complete, chunks=" + chunkCount
                            + ", content.length=" + fullResponse.content.length()
                            + ", finishReason=" + fullResponse.finishReason);

                    // Fallback: if streaming returned suspiciously short content
                    // (e.g. Ollama/Qwen with stream:true returning only "```"),
                    // retry with synchronous call.
                    if (fullResponse.content.length() < 20) {
                        Log.w(TAG, "generatePlan: streaming content too short ("
                                + fullResponse.content.length() + " chars), "
                                + "falling back to chatSync()...");
                        try {
                            LlmResponse syncResponse = httpClient.chatSync(request, adapter);
                            Log.i(TAG, "generatePlan: chatSync fallback, length="
                                    + syncResponse.content.length());
                            onStreamFinished(syncResponse);
                        } catch (Exception syncErr) {
                            Log.e(TAG, "generatePlan: chatSync fallback also failed", syncErr);
                            postError("LLM response too short and fallback failed: "
                                    + syncErr.getMessage());
                        }
                        return;
                    }

                    onStreamFinished(fullResponse);
                }

                @Override
                public void onError(@NonNull Exception e) {
                    if (cancelFlag.get()) {
                        Log.d(TAG, "Plan generation cancelled");
                        return;
                    }
                    Log.e(TAG, "Plan generation stream error", e);
                    postError(e.getMessage() != null ? e.getMessage()
                            : "Failed to generate plan");
                }
            }, cancelFlag);

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
     * Called when the streaming LLM response has completed.
     * Parses the plan and transitions to WAITING_APPROVE.
     * Runs on the background thread.
     */
    private void onStreamFinished(@NonNull LlmResponse response) {
        try {
            // Log full response for debugging parsing issues
            Log.i(TAG, "onStreamFinished: content length=" + response.content.length()
                    + ", finishReason=" + response.finishReason);
            if (response.content.length() <= 2000) {
                Log.d(TAG, "onStreamFinished: content=" + response.content);
            }

            // Parse plan from full response
            AgentPlan plan;
            try {
                plan = planParser.parseFromResponse(response);
            } catch (AgentPlanParser.PlanParseException e) {
                Log.e(TAG, "Plan parse failed. Raw response: " + response.content);
                throw e;
            }

            // Sanitize: inject terminal-launch HID step if in HID mode
            if ("hid".equals(promptBuilder.getExecutionMode())) {
                String targetOS = getTargetOs();
                plan = PlanSanitizer.sanitizeForHid(plan, targetOS);
                if (plan.steps.size() > 1) {
                    Log.i(TAG, "sanitizeHIDPlan: injected terminal launch step (OS="
                            + targetOS + ")");
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
            if (cancelFlag.get()) {
                Log.d(TAG, "Plan generation cancelled during parse");
                return;
            }
            Log.e(TAG, "Plan parsing failed", e);
            postError(e.getMessage() != null ? e.getMessage() : "Failed to parse plan");
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

                // Notify fragment to show Running state before execution starts
                String displayCommand = "terminal".equals(step.kind)
                        ? "$ " + (step.command != null ? step.command : step.title)
                        : " " + step.title;
                postToMain(() -> notifyStepStart(stepIndex, displayCommand));

                // Brief pause on background thread — gives the main thread time
                // to process notifyStepStart and render the "Running…" card
                // before the SSH command completes and fires notifyStepOutput.
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

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
                        // Mark as complete so the UI shows "完成" instead of "Running…"
                        // even when the command produced no output.
                        session.addExecutionCliMessage(lines);
                        postToMain(() -> notifyStepOutput(stepIndex, lines, true, true));
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
                        postToMain(() -> notifyStepOutput(stepIndex, lines, true, false));
                        stepError[0] = error;
                        latch.countDown();
                    }

                    @Override
                    public void onProgress(int idx, int total) {
                        postToMain(() -> notifyProgress(idx, total));
                    }
                });

                // Elapsed-time progress: show "(waiting… Xs)" every 3s so user
                // sees the card is alive during long commands.
                final java.util.concurrent.ScheduledExecutorService progressScheduler =
                        Executors.newSingleThreadScheduledExecutor();
                final long[] elapsedSec = {0};
                progressScheduler.scheduleAtFixedRate(() -> {
                    elapsedSec[0]++;
                    final List<String> progressLines = new ArrayList<>();
                    progressLines.add(displayCommand);
                    progressLines.add("(waiting... " + elapsedSec[0] + "s)");
                    postToMain(() -> notifyStepOutput(stepIndex, progressLines, false, null));
                }, 3, 3, TimeUnit.SECONDS);

                try {
                    boolean completed = latch.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    progressScheduler.shutdownNow();
                    if (!completed) {
                        stepError[0] = "Step timed out after " + STEP_TIMEOUT_SECONDS + "s";
                        // Update CLI card to show timeout instead of stuck on "Running…"
                        final List<String> timeoutLines = new ArrayList<>();
                        timeoutLines.add(displayCommand);
                        timeoutLines.add("⚠️ " + stepError[0]);
                        postToMain(() -> notifyStepOutput(stepIndex, timeoutLines, true, false));
                    }
                } catch (InterruptedException e) {
                    progressScheduler.shutdownNow();
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
                    // Retry: re-notify step start so the card shows "Running…" again
                    postToMain(() -> notifyStepStart(stepIndex, displayCommand));
                    try { Thread.sleep(500); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); return;
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

        // All steps completed — notify completion.
        // Fragment's onExecutionComplete will trigger summarize with a delay
        // measured from after the terminal card layout pass, not from here.
        postToMain(this::notifyExecutionComplete);
    }

    /**
     * Public entry point for Fragment to trigger summarize phase.
     * Spawns a background thread so the LLM network call never blocks the UI.
     * Called after terminal card layout pass completes.
     */
    public void startSummarize() {
        if (cancelFlag.get()) return;
        new Thread(this::summarize, "AgentSummarize").start();
    }

    /**
     * Summarize execution results via LLM with streaming.
     * Runs on background thread. Streams tokens to Fragment for per-char display.
     */
    private void summarize() {
        // Read AI settings from unified config provider
        AIConfigProvider config = AIConfigProvider.getInstance(appContext);
        String endpoint = config.getEndpoint();
        String model = config.getModel();
        String apiKey = config.getApiKey();
        ProviderAdapter adapter = config.getAdapter();

        LlmHttpClient httpClient = new LlmHttpClient(
                apiKey, endpoint, adapter,
                LlmHttpClient.DEFAULT_CONNECT_TIMEOUT_MS,
                AGENT_READ_TIMEOUT_MS);

        // Build summarize request
        CharSequence firstMsgText = session.getMessages().isEmpty() ? null
                : session.getMessages().get(0).text;
        String originalPrompt = firstMsgText != null ? firstMsgText.toString() : "";
        List<String[]> results = new ArrayList<>();
        for (AgentMessage msg : session.getMessages()) {
            if (msg.type == AgentMessage.Type.EXECUTION_CLI && !msg.terminalLines.isEmpty()) {
                String cmd = msg.terminalLines.get(0);
                String output = msg.terminalLines.size() > 1
                        ? msg.terminalLines.get(1) : "";
                results.add(new String[]{cmd, output});
            }
        }

        String summarizePrompt = promptBuilder.buildSummarizePrompt(
                originalPrompt, results);
        LlmRequest request = new LlmRequest(model);
        request.addSystemMessage(summarizePrompt);
        request.addUserMessage("Summarize the results.");
        request.temperature = 0.3;
        request.maxTokens = 1024;

        Log.i(TAG, "summarize: calling LLM (streaming)...");

        // For HID mode: no output to summarize, skip LLM call
        final boolean isHidModeFinal = "hid".equals(promptBuilder.getExecutionMode());
        if (isHidModeFinal || results.isEmpty()) {
            final int totalSteps = currentPlan != null ? currentPlan.steps.size() : 0;
            postToMain(() -> {
                notifySummaryToken("✅ Completed " + totalSteps + " step(s).");
                notifySummaryComplete();
                session.addAssistantMessage("✅ Completed " + totalSteps + " step(s).");
                transitionTo(AgentState.IDLE);
            });
            return;
        }

        httpClient.chatStream(request, adapter, new LlmResult() {
            @Override
            public void onChunk(@NonNull LlmResponse chunk) {
                if (!chunk.content.isEmpty()) {
                    postToMain(() -> notifySummaryToken(chunk.content));
                }
            }

            @Override
            public void onComplete(@NonNull LlmResponse fullResponse) {
                Log.i(TAG, "summarize: stream complete, length=" + fullResponse.content.length());

                // Same fallback as generatePlan: if streaming returned suspiciously
                // short content (Ollama/Qwen with stream:true), retry with chatSync().
                if (fullResponse.content.length() < 20) {
                    Log.w(TAG, "summarize: streaming content too short ("
                            + fullResponse.content.length() + " chars), "
                            + "falling back to chatSync()...");
                    try {
                        LlmResponse syncResponse = httpClient.chatSync(request, adapter);
                        Log.i(TAG, "summarize: chatSync fallback, length="
                                + syncResponse.content.length());
                        session.addAssistantMessage(syncResponse.content);
                        // Reset Fragment's summary buffer state before delivering full content,
                        // because earlier streaming onChunk calls may have set summaryDelayScheduled=true
                        // which would cause appendSummaryToken to drop the full response.
                        postToMain(() -> {
                            notifySummaryReset();
                            notifySummaryToken(syncResponse.content);
                            notifySummaryComplete();
                            transitionTo(AgentState.IDLE);
                        });
                    } catch (Exception syncErr) {
                        Log.e(TAG, "summarize: chatSync fallback also failed", syncErr);
                        postToMain(() -> {
                            notifySummaryReset();
                            notifySummaryToken("️ Summarize failed: " + syncErr.getMessage());
                            notifySummaryComplete();
                            transitionTo(AgentState.IDLE);
                        });
                    }
                    return;
                }

                session.addAssistantMessage(fullResponse.content);
                postToMain(() -> {
                    notifySummaryComplete();
                    transitionTo(AgentState.IDLE);
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (cancelFlag.get()) return;
                Log.e(TAG, "summarize: error", e);
                postToMain(() -> {
                    notifySummaryToken("⚠️ Failed to summarize: " + e.getMessage());
                    notifySummaryComplete();
                    transitionTo(AgentState.IDLE);
                });
            }
        }, cancelFlag);
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

    private void notifyStepOutput(int stepIndex, @NonNull List<String> lines,
                                   boolean isComplete, @Nullable Boolean success) {
        if (listener != null) listener.onStepOutput(stepIndex, lines, isComplete, success);
    }

    private void notifyStepStart(int stepIndex, @NonNull String command) {
        if (listener != null) listener.onStepStart(stepIndex, command);
    }

    private void notifyToken(@NonNull String token) {
        if (listener != null) listener.onToken(token);
    }

    private void notifySummaryToken(@NonNull String token) {
        if (listener != null) listener.onSummaryToken(token);
    }

    private void notifySummaryComplete() {
        if (listener != null) listener.onSummaryComplete();
    }

    private void notifySummaryReset() {
        if (listener != null) listener.onSummaryReset();
    }

    @NonNull
    private static String getProviderName(int index) {
        if (index >= 0 && index < PROVIDER_NAMES.length) {
            return PROVIDER_NAMES[index];
        }
        return "OpenAI";
    }

    /**
     * Check if the endpoint is a local provider (Ollama, etc.) that does not
     * support streaming reliably. Local providers return very short content via
     * SSE and cause stutter UX when combined with fallback logic.
     * For local providers we skip streaming and use chatSync() directly.
     */
    private static boolean isLocalEndpoint(@NonNull String endpoint) {
        String lower = endpoint.toLowerCase();
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains(":11434")
                || lower.startsWith("http://192.168.")
                || lower.startsWith("http://10.")
                || lower.contains("host.docker.internal");
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
