package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.llm.LlmRequest;
import com.openterface.keymod.agent.llm.LlmResponse;
import com.openterface.keymod.agent.llm.LlmResult;
import com.openterface.keymod.agent.llm.ProviderAdapter;
import com.openterface.keymod.agent.settings.AIConfigProvider;
import com.openterface.terminal.CredentialProfile;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates execution plans from user prompts via LLM streaming.
 *
 * <p>Extracted from {@code AgentController.generatePlan()} and
 * {@code AgentController.onStreamFinished()} — handles prompt routing
 * (terminal vs HID), OS auto-detection, LLM streaming with chatSync
 * fallback, plan parsing, HID sanitization, and plan truncation.</p>
 *
 * <p>Communicates results back to AgentController via {@link Callback}.
 * State transitions (WAITING_APPROVE, ERROR) are the controller's
 * responsibility.</p>
 */
final class PlanGenerationUseCase {

    private static final String TAG = "PlanGenUseCase";

    private final Context appContext;
    private final AgentEnvironment environment;
    private final AgentPromptBuilder promptBuilder;
    private final AgentPlanParser planParser;
    private final AgentSession session;
    private final TraceManager traceManager;
    private final AgentController.AgentListener listener;
    private final AtomicBoolean cancelFlag;
    private final Handler mainHandler;
    private final Callback callback;
    private final int maxSteps;

    /** Detected target OS from SSH connection (null if not detected yet). */
    @Nullable private volatile OsDetector.DetectedOS detectedOs;

    /**
     * @param appContext      application context
     * @param environment     host environment (SSH profile, SshClient, BLE)
     * @param promptBuilder   builds LLM prompts
     * @param planParser      parses LLM response into AgentPlan
     * @param session         message store (records assistant plan message)
     * @param traceManager    trace session lifecycle
     * @param listener        UI callback (onToken for streaming)
     * @param cancelFlag      cancellation flag shared with AgentController
     * @param mainHandler     main thread handler for posting UI updates
     * @param maxSteps        maximum steps before truncation
     * @param callback        notifies controller of plan/error/truncation/OS detection
     */
    PlanGenerationUseCase(
            @NonNull Context appContext,
            @NonNull AgentEnvironment environment,
            @NonNull AgentPromptBuilder promptBuilder,
            @NonNull AgentPlanParser planParser,
            @NonNull AgentSession session,
            @NonNull TraceManager traceManager,
            @NonNull AgentController.AgentListener listener,
            @NonNull AtomicBoolean cancelFlag,
            @NonNull Handler mainHandler,
            int maxSteps,
            @NonNull Callback callback) {
        this.appContext = appContext;
        this.environment = environment;
        this.promptBuilder = promptBuilder;
        this.planParser = planParser;
        this.session = session;
        this.traceManager = traceManager;
        this.listener = listener;
        this.cancelFlag = cancelFlag;
        this.mainHandler = mainHandler;
        this.maxSteps = maxSteps;
        this.callback = callback;
    }

    /**
     * Get the target OS name. Priority:
     * 1. User's explicit OS selection from TargetSettingsSheet (agent_prefs)
     * 2. Auto-detected OS from SSH connection
     * 3. Active profile's targetOs
     * 4. Default fallback ("macos")
     */
    @NonNull
    String getTargetOs() {
        // Priority 1: User's explicit OS choice
        SharedPreferences prefs = appContext.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE);
        String userOs = prefs.getString("agent_target_os", "");
        if (userOs != null && !userOs.isEmpty()) {
            return userOs;
        }

        // Priority 2: Auto-detected OS from SSH
        if (detectedOs != null) {
            return detectedOs.getCode();
        }

        // Priority 3: Profile-level OS
        CredentialProfile profile = environment.getActiveSshProfile();
        if (profile != null) {
            String os = profile.getTargetOs();
            if (os != null && !os.isEmpty()) return os;
        }

        // Priority 4: Default fallback
        return "macos"; // TODO(release): revert default to "linux"
    }

    /**
     * Get the custom prompt for the given execution mode.
     */
    @NonNull
    private String getCustomPromptForMode(@NonNull String mode,
                                           @NonNull String customTerminalPrompt,
                                           @NonNull String customHidPrompt) {
        switch (mode) {
            case "hid":
                return customHidPrompt;
            case "terminal":
                return customTerminalPrompt;
            default:
                return "";
        }
    }

    /**
     * Generate an execution plan from user prompt.
     * Call on background thread.
     *
     * <p>Prompt routing: if an SSH profile is active, uses terminal mode prompt.
     * Otherwise uses HID mode prompt (keyboard-only, no SSH).</p>
     *
     * @param userPrompt          the user's request
     * @param customTerminalPrompt custom prompt for terminal mode (may be empty)
     * @param customHidPrompt     custom prompt for HID mode (may be empty)
     */
    void generatePlan(@NonNull String userPrompt,
                       @NonNull String customTerminalPrompt,
                       @NonNull String customHidPrompt) {
        AIConfigProvider config = AIConfigProvider.getInstance(appContext);
        String model = config.getModel();
        ProviderAdapter adapter = config.getAdapter();

        LlmHttpClient httpClient = new LlmHttpClient(
                config.getApiKey(), config.getEndpoint(), adapter,
                LlmHttpClient.DEFAULT_CONNECT_TIMEOUT_MS,
                LlmClientFactory.AGENT_READ_TIMEOUT_MS);

        // ─ Prompt routing: detect SSH profile to choose execution mode ──
        CredentialProfile profile = environment.getActiveSshProfile();
        if (profile != null) {
            promptBuilder.setExecutionMode("terminal");
            promptBuilder.setActiveProfile(profile);
            Log.i(TAG, "Prompt routing: terminal mode (SSH profile: "
                    + profile.getDisplayLabel() + ")");

            // ─ OS Auto-detection: try to detect target OS from SSH connection ──
            detectedOs = null;
            try {
                String fallbackOs = profile.getTargetOs();
                if (fallbackOs == null || fallbackOs.isEmpty()) {
                    SharedPreferences prefs = appContext.getSharedPreferences(
                            "agent_prefs", Context.MODE_PRIVATE);
                    fallbackOs = prefs.getString("agent_target_os", "macos");
                }
                com.openterface.terminal.SshClient sshClient = environment.getSshClient();
                if (sshClient != null) {
                    detectedOs = OsDetector.detectOsSync(sshClient, fallbackOs);
                    Log.i(TAG, "OS auto-detected: " + detectedOs.getDisplayName()
                            + " (code: " + detectedOs.getCode() + ")");
                    callback.onOsDetected(detectedOs);
                } else {
                    Log.w(TAG, "OS auto-detection skipped: no SSH client available");
                }
            } catch (Exception e) {
                Log.w(TAG, "OS auto-detection failed, using profile fallback", e);
            }
        } else {
            promptBuilder.setExecutionMode("hid");
            promptBuilder.setActiveProfile(null);
            Log.i(TAG, "Prompt routing: HID mode (no SSH profile)");
        }

        // Build request with custom prompt support
        String customPrompt = getCustomPromptForMode(
                promptBuilder.getExecutionMode(), customTerminalPrompt, customHidPrompt);
        LlmRequest request = promptBuilder.buildRequest(model, userPrompt, customPrompt);

        // Start trace for plan generation
        traceManager.newTrace("generate_plan");

        Log.i(TAG, traceManager.formatLogMessage(
                "generatePlan: mode=" + promptBuilder.getExecutionMode()
                        + ", model=" + model + ", messages=" + request.messages.size()));

        try {
            Log.i(TAG, traceManager.formatLogMessage("generatePlan: calling LLM (streaming)..."));

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
                        postToMain(() -> {
                            if (listener != null) listener.onToken(chunk.content);
                        });
                    }
                }

                @Override
                public void onComplete(@NonNull LlmResponse fullResponse) {
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
                            callback.onError("LLM response too short and fallback failed: "
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
                    callback.onError(e.getMessage() != null ? e.getMessage()
                            : "Failed to generate plan");
                }
            }, cancelFlag);

        } catch (Exception e) {
            if (cancelFlag.get()) {
                Log.d(TAG, "Plan generation cancelled");
                return;
            }
            Log.e(TAG, "Plan generation failed", e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to generate plan");
        }
    }

    /**
     * Called when the streaming LLM response has completed.
     * Parses the plan and notifies callback.
     * Runs on the background thread.
     */
    private void onStreamFinished(@NonNull LlmResponse response) {
        try {
            // Log full response for debugging parsing issues
            Log.i(TAG, traceManager.formatLogMessage(
                    "onStreamFinished: content length=" + response.content.length()
                            + ", finishReason=" + response.finishReason));
            if (response.content.length() <= 2000) {
                Log.d(TAG, "onStreamFinished: content=" + response.content);
            }

            // Parse plan from full response
            AgentPlan plan;
            try {
                plan = planParser.parseFromResponse(response);
            } catch (AgentPlanParser.PlanParseException e) {
                Log.e(TAG, "Plan parse failed. Raw response: " + response.content);
                traceManager.endTrace(false, "error", "parse_failed");
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
                postToMain(() -> {
                    if (listener != null) listener.onPlanTruncated(truncatedTo);
                });
            }

            // Store plan and record assistant message (on main thread for thread safety)
            final AgentPlan finalPlan = plan;
            postToMain(() -> session.addAssistantMessage(finalPlan.summary));

            // End trace with success
            traceManager.endTrace(true, "steps", String.valueOf(finalPlan.steps.size()));

            // Notify controller → transitions to WAITING_APPROVE on main thread
            callback.onPlanReady(finalPlan);

        } catch (Exception e) {
            if (cancelFlag.get()) {
                Log.d(TAG, "Plan generation cancelled during parse");
                return;
            }
            Log.e(TAG, "Plan parsing failed", e);
            // End trace with failure (if not already ended)
            if (traceManager.getCurrentTraceId() != null) {
                traceManager.endTrace(false, "error",
                        e.getMessage() != null ? e.getMessage() : "unknown");
            }
            callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to parse plan");
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
        /** Plan generated successfully — controller should transition to WAITING_APPROVE. */
        void onPlanReady(@NonNull AgentPlan plan);

        /** Error occurred — controller should transition to ERROR. */
        void onError(@NonNull String message);

        /** Plan was truncated to fit maxSteps — controller should notify UI. */
        void onPlanTruncated(int maxSteps);

        /** OS was auto-detected from SSH — controller should store the result. */
        void onOsDetected(@NonNull OsDetector.DetectedOS os);
    }
}
