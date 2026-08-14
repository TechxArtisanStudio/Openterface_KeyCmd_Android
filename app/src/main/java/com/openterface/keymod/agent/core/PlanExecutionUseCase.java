package com.openterface.keymod.agent.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.executor.TerminalToolExecutor;
import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.llm.LlmRequest;
import com.openterface.keymod.agent.llm.LlmResponse;
import com.openterface.keymod.agent.settings.AIConfigProvider;
import com.openterface.keymod.agent.ui.AgentMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes plan steps sequentially with retry logic.
 *
 * <p>Extracted from {@code AgentController.executePlan()} and
 * {@code AgentController.requestRetryPlan()} — handles step iteration,
 * per-step timeout via CountDownLatch, elapsed-time progress updates,
 * LLM-level retry on failure (generating alternative commands), and
 * post-retry continuation of remaining steps.</p>
 *
 * <p>Owns its own single-thread executor for step execution and retry
 * LLM calls. The executor is shut down via {@link #shutdown()}.</p>
 */
final class PlanExecutionUseCase {

    private static final String TAG = "PlanExecUseCase";

    /**
     * Timeout for waiting on a single step's execution callback.
     * Must exceed {@link TerminalToolExecutor#DEFAULT_TIMEOUT_MS} so the SSH
     * command can finish before the latch fires. The extra 30 s covers
     * network latency, callback dispatch, and inter-step delay.
     */
    private static final long STEP_TIMEOUT_SECONDS =
            (TerminalToolExecutor.DEFAULT_TIMEOUT_MS / 1000) + 30; // 90 s

    private final Context appContext;
    private final AgentPromptBuilder promptBuilder;
    private final AgentPlanParser planParser;
    private final AgentSession session;
    private final TraceManager traceManager;
    private final AgentController.AgentListener listener;
    private final AtomicBoolean cancelFlag;
    private final Handler mainHandler;
    private final int maxSteps;
    private final int maxRetries;
    private final Callback callback;

    @Nullable private AgentToolExecutor toolExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Execution state ──────────────────────────────────────────────────

    @Nullable private AgentPlan currentPlan;
    private int currentStepIndex = 0;
    private int planRetryCount = 0;
    /** Steps from the original plan not yet executed when retry was triggered. */
    @Nullable private List<AgentPlan.Step> pendingStepsAfterRetry;
    @Nullable private Future<?> runningTask;

    /** Collects execution results for conversation context (P1-7 enrichment). */
    private final List<String[]> executionResults = new ArrayList<>();

    /**
     * @param appContext    application context (for LLM config)
     * @param promptBuilder builds retry prompts + terminal context
     * @param planParser    parses LLM retry response into AgentPlan
     * @param session       message store (records retry assistant messages)
     * @param traceManager  trace session lifecycle
     * @param listener      UI callback (step output, progress, retry, etc.)
     * @param cancelFlag    cancellation flag shared with AgentController
     * @param mainHandler   main thread handler for posting UI updates
     * @param maxSteps      max steps before truncation (for retry plan)
     * @param maxRetries    max LLM-level retries on step failure
     * @param callback      notifies controller of completion/errors/retry state
     */
    PlanExecutionUseCase(
            @NonNull Context appContext,
            @NonNull AgentPromptBuilder promptBuilder,
            @NonNull AgentPlanParser planParser,
            @NonNull AgentSession session,
            @NonNull TraceManager traceManager,
            @NonNull AgentController.AgentListener listener,
            @NonNull AtomicBoolean cancelFlag,
            @NonNull Handler mainHandler,
            int maxSteps,
            int maxRetries,
            @NonNull Callback callback) {
        this.appContext = appContext;
        this.promptBuilder = promptBuilder;
        this.planParser = planParser;
        this.session = session;
        this.traceManager = traceManager;
        this.listener = listener;
        this.cancelFlag = cancelFlag;
        this.mainHandler = mainHandler;
        this.maxSteps = maxSteps;
        this.maxRetries = maxRetries;
        this.callback = callback;
    }

    /** Set the tool executor for running plan steps. */
    void setToolExecutor(@Nullable AgentToolExecutor executor) {
        this.toolExecutor = executor;
    }

    /** Cancel any running task and shut down the executor. */
    void shutdown() {
        if (runningTask != null && !runningTask.isDone()) {
            runningTask.cancel(true);
        }
        runningTask = null;
        if (toolExecutor != null) {
            toolExecutor.cancel();
        }
        executor.shutdownNow();
    }

    /** Reset execution state for a fresh run. */
    void resetState() {
        currentStepIndex = 0;
        planRetryCount = 0;
        pendingStepsAfterRetry = null;
    }

    /**
     * Begin executing the given plan.
     * Caller is responsible for transitioning to EXECUTING before calling this.
     * Runs the execution loop on the background executor thread.
     *
     * @param plan     the plan to execute
     * @param targetOs target OS for retry plan generation
     */
    void executePlan(@NonNull AgentPlan plan, @NonNull String targetOs) {
        this.currentPlan = plan;
        this.currentStepIndex = 0;
        this.executionResults.clear();  // Clear for fresh execution
        runningTask = executor.submit(() -> executePlanInternal(targetOs));
    }

    /**
     * Re-execute the current plan from the beginning (no callback transition).
     * Used by AgentController.reexecutePlan().
     *
     * @param targetOs target OS for retry plan generation
     */
    void reexecutePlan(@NonNull String targetOs) {
        currentStepIndex = 0;
        planRetryCount = 0;
        cancelFlag.set(false);
        this.executionResults.clear();  // Clear for fresh execution
        runningTask = executor.submit(() -> executePlanInternal(targetOs));
    }

    /**
     * Internal execution loop. Runs on the background executor thread.
     * May recursively re-enter via retry or post-retry continuation.
     */
    private void executePlanInternal(@NonNull String targetOs) {
        if (currentPlan == null || currentPlan.isEmpty()) {
            callback.onError("No plan to execute");
            return;
        }

        if (toolExecutor == null) {
            postToMain(() -> {
                if (listener != null) listener.onError("Tool executor not implemented");
                traceManager.endSession(false, "no_tool_executor");
                callback.onTransitionToIdle();
            });
            return;
        }

        // HID mode: no LLM retry (no output to inform alternatives)
        boolean isHidMode = "hid".equals(promptBuilder.getExecutionMode());

        // Collect failed steps for potential LLM retry at the end
        List<String[]> failedSteps = new ArrayList<>();

        for (int i = currentStepIndex; i < currentPlan.steps.size(); i++) {
            currentStepIndex = i;
            final int stepIndex = i;
            AgentPlan.Step step = currentPlan.steps.get(i);

            // Trace each step execution
            traceManager.newTrace("execute_step_" + stepIndex);

            // Report progress
            postToMain(() -> {
                if (listener != null) listener.onExecutionProgress(stepIndex, currentPlan.steps.size());
            });

            // Use CountDownLatch instead of busy-wait
            final CountDownLatch latch = new CountDownLatch(1);
            final String[] stepError = {null};

            // Streaming output buffer — accumulates lines as they arrive from SSH.
            // When non-empty at onSuccess time, we reuse it instead of re-parsing.
            final StringBuilder outputBuffer = new StringBuilder();
            // Forward reference to the progress scheduler so onOutputLine can
            // cancel it as soon as real output starts arriving.
            final AtomicReference<ScheduledExecutorService> schedulerRef =
                    new AtomicReference<>();
            final AtomicBoolean hasStreamingOutput = new AtomicBoolean(false);

            // Notify fragment to show Running state before execution starts
            String displayCommand = "terminal".equals(step.kind)
                    ? "$ " + (step.command != null ? step.command : step.title)
                    : " " + step.title;
            postToMain(() -> {
                if (listener != null) listener.onStepStart(stepIndex, displayCommand);
            });

            // Brief pause — gives the main thread time to process notifyStepStart
            // and render the "Running…" card before the command completes.
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            toolExecutor.execute(step, new AgentToolExecutor.ExecutionCallback() {
                @Override
                public void onSuccess(@NonNull String output) {
                    // When streaming was used, the UI already has the output lines
                    // appended incrementally. Just mark the card complete and save
                    // the full output to session / conversation context.
                    String effectiveOutput = hasStreamingOutput.get()
                            ? outputBuffer.toString()
                            : output;

                    // Save to conversation context (P1-7 enrichment)
                    String cmd = step.command != null ? step.command : step.title;
                    String truncatedOutput = effectiveOutput != null
                            && effectiveOutput.length() > 200
                            ? effectiveOutput.substring(0, 200) + "... (truncated)"
                            : (effectiveOutput != null ? effectiveOutput : "");
                    executionResults.add(new String[]{cmd, truncatedOutput, "success"});

                    // Always save to session history — SummaryUseCase reads from
                    // session.getMessages() to collect results for summarization.
                    // The UI card is managed separately by AgentFragment.chatMessages
                    // (created via onStepStart, updated via onOutputLine), so adding
                    // to session does NOT create duplicate UI cards.
                    List<String> fullLines = new ArrayList<>();
                    if ("terminal".equals(step.kind)) {
                        fullLines.add("$ " + (step.command != null ? step.command : step.title));
                    } else {
                        fullLines.add("🔑 " + step.title);
                    }
                    if (effectiveOutput != null && !effectiveOutput.isEmpty()) {
                        for (String line : effectiveOutput.split("\n")) {
                            fullLines.add(line);
                        }
                    }
                    postToMain(() -> session.addExecutionCliMessage(fullLines));

                    // Mark the Running card as complete (UI keeps existing lines).
                    postToMain(() -> {
                        if (listener != null)
                            listener.onStepOutput(stepIndex, Collections.emptyList(), true, true);
                    });
                    traceManager.endTrace(true);
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
                    // Collect result for conversation context (P1-7 enrichment)
                    String cmd = step.command != null ? step.command : step.title;
                    executionResults.add(new String[]{cmd, error, "failed"});

                    // Always save to session for SummaryUseCase (see onSuccess comment).
                    postToMain(() -> session.addExecutionCliMessage(lines));
                    postToMain(() -> {
                        if (listener != null) listener.onStepOutput(stepIndex, lines, true, false);
                    });
                    stepError[0] = error;
                    traceManager.endTrace(false, "error", error);
                    latch.countDown();
                }

                @Override
                public void onProgress(int idx, int total) {
                    postToMain(() -> {
                        if (listener != null) listener.onExecutionProgress(idx, total);
                    });
                }

                /** Streaming output — fires for each line as SSH delivers it. */
                @Override
                public void onOutputLine(int idx, @NonNull String line) {
                    outputBuffer.append(line).append('\n');
                    hasStreamingOutput.set(true);

                    // Cancel the "(waiting...)" timer — real output has arrived.
                    java.util.concurrent.ScheduledExecutorService sched = schedulerRef.get();
                    if (sched != null) {
                        sched.shutdownNow();
                    }

                    // Forward the single line to the UI so it appends to the Running card.
                    postToMain(() -> {
                        if (listener != null)
                            listener.onStepOutput(stepIndex,
                                    Collections.singletonList(line),
                                    false, null);
                    });
                }
            });

            // Elapsed-time progress: show "(waiting… Xs)" every 3s
            final java.util.concurrent.ScheduledExecutorService progressScheduler =
                    Executors.newSingleThreadScheduledExecutor();
            schedulerRef.set(progressScheduler);
            final long[] elapsedSec = {0};
            progressScheduler.scheduleAtFixedRate(() -> {
                elapsedSec[0]++;
                final List<String> progressLines = new ArrayList<>();
                progressLines.add(displayCommand);
                progressLines.add("(waiting... " + elapsedSec[0] + "s)");
                postToMain(() -> {
                    if (listener != null)
                        listener.onStepOutput(stepIndex, progressLines, false, null);
                });
            }, 3, 3, TimeUnit.SECONDS);

            try {
                boolean completed = latch.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                progressScheduler.shutdownNow();
                if (!completed) {
                    stepError[0] = "Step timed out after " + STEP_TIMEOUT_SECONDS + "s";
                    final List<String> timeoutLines = new ArrayList<>();
                    timeoutLines.add(displayCommand);
                    timeoutLines.add(stepError[0]);
                    postToMain(() -> {
                        if (listener != null)
                            listener.onStepOutput(stepIndex, timeoutLines, true, false);
                    });
                }
            } catch (InterruptedException e) {
                progressScheduler.shutdownNow();
                Thread.currentThread().interrupt();
                return; // cancelled
            }

            if (stepError[0] != null) {
                // Record this failure for potential LLM retry
                String failedCommand = step.command != null ? step.command
                        : (step.title != null ? step.title : "step " + (stepIndex + 1));
                failedSteps.add(new String[]{failedCommand, stepError[0]});

                // Check if we should do LLM-level retry
                // HID mode: no LLM retry (no output feedback available)
                if (!isHidMode && planRetryCount < maxRetries) {
                    // Save remaining unexecuted steps before retry replaces the plan
                    List<AgentPlan.Step> remaining = new ArrayList<>();
                    for (int j = i + 1; j < currentPlan.steps.size(); j++) {
                        remaining.add(currentPlan.steps.get(j));
                    }
                    if (!remaining.isEmpty()) {
                        pendingStepsAfterRetry = remaining;
                        Log.i(TAG, "Saved " + remaining.size()
                                + " remaining steps for post-retry execution");
                    }
                    // Break out of the step loop; invoke LLM retry
                    requestRetryPlan(failedSteps, targetOs);
                    return;
                }

                // No more retries allowed — report final failure
                callback.onError("Step " + (stepIndex + 1) + " failed"
                        + (planRetryCount >= maxRetries
                                ? " after " + maxRetries + " LLM retries"
                                : "")
                        + ": " + stepError[0]);
                return;
            }

            // Step interval: 0.5s delay between steps
            if (i < currentPlan.steps.size() - 1 && !cancelFlag.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // All steps in current plan completed. Check for pending steps
        // saved from a pre-retry original plan.
        if (pendingStepsAfterRetry != null && !pendingStepsAfterRetry.isEmpty()) {
            List<AgentPlan.Step> pending = pendingStepsAfterRetry;
            pendingStepsAfterRetry = null;
            int resumeFrom = currentPlan.steps.size();
            currentPlan.steps.addAll(pending);
            currentStepIndex = resumeFrom;
            Log.i(TAG, "Continuing with " + pending.size()
                    + " remaining steps from original plan (total now: "
                    + currentPlan.steps.size() + ")");
            executePlanInternal(targetOs);
            return;
        }

        // All steps completed — notify completion with results (P1-7 enrichment)
        Log.i(TAG, traceManager.formatLogMessage("executePlan: all steps completed, results="
                + executionResults.size()));
        final List<String[]> resultsCopy = new ArrayList<>(executionResults);
        callback.onExecutionCompleted(resultsCopy);
        postToMain(() -> {
            if (listener != null) listener.onExecutionComplete();
        });
    }

    /**
     * Request LLM to generate alternative commands after step failures.
     * Called from executePlanInternal() on the background thread.
     * The new plan replaces currentPlan and execution restarts from step 0.
     *
     * @param failedSteps pairs of (command/description, error message)
     * @param targetOs    target OS for retry prompt and plan sanitization
     */
    private void requestRetryPlan(@NonNull List<String[]> failedSteps,
                                   @NonNull String targetOs) {
        planRetryCount++;
        callback.onTransitionToRetrying();
        traceManager.newTrace("retry_plan");

        // Collect original user prompt from session
        String originalPrompt = "";
        for (AgentMessage msg : session.getMessages()) {
            if (msg.type == AgentMessage.Type.USER) {
                originalPrompt = msg.text != null ? msg.text.toString() : "";
                break;
            }
        }

        final String terminalContext = promptBuilder.buildTerminalModeContext();
        final String prompt = originalPrompt;

        postToMain(() -> {
            if (listener != null) listener.onRetry(planRetryCount, maxRetries);
        });

        try {
            // Check cancel before making LLM call
            if (cancelFlag.get()) {
                Log.d(TAG, "Retry plan cancelled");
                return;
            }

            AIConfigProvider config = AIConfigProvider.getInstance(appContext);

            // Local models don't support chatSync reliably
            if (config.getEndpoint().startsWith("local://")) {
                callback.onError("Local model does not support smart retry");
                return;
            }

            LlmHttpClient httpClient = new LlmHttpClient(
                    config.getApiKey(), config.getEndpoint(), config.getAdapter(),
                    LlmHttpClient.DEFAULT_CONNECT_TIMEOUT_MS,
                    LlmClientFactory.AGENT_READ_TIMEOUT_MS);

            // Build retry request
            String retryPrompt = promptBuilder.buildRetryPrompt(
                    prompt, failedSteps, targetOs);
            LlmRequest request = new LlmRequest(config.getModel());
            request.addSystemMessage(terminalContext + "\n\n" + retryPrompt);
            request.addUserMessage("Please provide alternative commands.");
            request.temperature = 0.3;
            request.maxTokens = 2048;

            Log.i(TAG, traceManager.formatLogMessage(
                    "requestRetryPlan: calling LLM (attempt "
                            + planRetryCount + "/" + maxRetries + ")"));

            // Synchronous call — retry doesn't need streaming UX
            LlmResponse response = httpClient.chatSync(request, config.getAdapter());

            if (cancelFlag.get()) {
                Log.d(TAG, "Retry plan cancelled during LLM call");
                return;
            }

            // Parse the response into a new plan
            AgentPlan newPlan = planParser.parseFromResponse(response);

            // Sanitize: inject terminal-launch HID step if in HID mode
            if ("hid".equals(promptBuilder.getExecutionMode())) {
                newPlan = PlanSanitizer.sanitizeForHid(newPlan, targetOs);
            }
            if (newPlan.steps.size() > maxSteps) {
                newPlan = newPlan.truncateTo(maxSteps);
            }

            // Replace current plan and restart execution
            currentPlan = newPlan;
            currentStepIndex = 0;

            final int retryNum = planRetryCount;
            final String retrySummary = newPlan.summary;
            postToMain(() -> session.addAssistantMessage("🔄 Retry " + retryNum + "/" + maxRetries
                    + ": " + retrySummary));

            callback.onTransitionToExecuting();
            executePlanInternal(targetOs);

        } catch (AgentPlanParser.PlanParseException e) {
            Log.e(TAG, "Retry plan parse failed", e);
            callback.onError("Retry failed: could not parse LLM response");
        } catch (Exception e) {
            Log.e(TAG, "Retry plan generation failed", e);
            String detail = e.getMessage();
            if (detail == null || detail.isEmpty()) {
                detail = e.getClass().getSimpleName();
            }
            callback.onError("Retry failed: " + detail);
        }
    }

    /** Post a runnable to the main thread, or run inline if already on main. */
    private void postToMain(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    /** Callback interface for communicating results back to AgentController. */
    interface Callback {
        /** Step execution failed fatally — controller should transition to ERROR. */
        void onError(@NonNull String message);

        /** Transition to RETRYING state. */
        void onTransitionToRetrying();

        /** Transition to EXECUTING state. */
        void onTransitionToExecuting();

        /** Transition to IDLE state (for toolExecutor==null case). */
        void onTransitionToIdle();

        /**
         * All steps completed successfully.
         * @param results list of [command, output, status] for each step
         */
        void onExecutionCompleted(@NonNull List<String[]> results);
    }
}
