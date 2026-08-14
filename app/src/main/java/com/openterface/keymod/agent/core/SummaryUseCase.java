package com.openterface.keymod.agent.core;

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
import com.openterface.keymod.agent.ui.AgentMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates a summary of execution results via LLM with streaming.
 *
 * <p>Extracted from {@code AgentController.summarize()} — handles the
 * full summarize lifecycle: data capping, LLM streaming, multi-layer
 * fallback (chatSync → client-side), and UI notification.</p>
 *
 * <p>All listener callbacks and session mutations are dispatched on the
 * main thread via the provided Handler. Trace lifecycle events
 * (endSession) are called on the background thread.</p>
 */
final class SummaryUseCase {

    private static final String TAG = "SummaryUseCase";

    private final AgentSession session;
    private final AgentPromptBuilder promptBuilder;
    private final TraceManager traceManager;
    private final LlmClientFactory clientFactory;
    private final AgentController.AgentListener listener;
    private final AtomicBoolean cancelFlag;
    private final Handler mainHandler;
    private final Callback callback;

    /** Current plan — read for step count in HID/empty skip. */
    @Nullable private final AgentPlan currentPlan;

    /** Start index in session messages — only messages from this index onward
     *  are collected as execution results (current round only). */
    private final int executionStartIndex;

    /**
     * @param session        message store (read execution results, write summary)
     * @param promptBuilder  builds summarize prompt
     * @param traceManager   trace session lifecycle
     * @param clientFactory  creates LlmHttpClient instances
     * @param listener       UI callback (onSummaryToken, onSummaryComplete, onSummaryReset)
     * @param cancelFlag     cancellation flag shared with AgentController
     * @param mainHandler    main thread handler for posting UI updates
     * @param currentPlan    current plan (nullable — for step count in HID mode)
     * @param executionStartIndex session message index before execution started
     * @param callback       notifies when summarize is done (triggers state transition to IDLE)
     */
    SummaryUseCase(
            @NonNull AgentSession session,
            @NonNull AgentPromptBuilder promptBuilder,
            @NonNull TraceManager traceManager,
            @NonNull LlmClientFactory clientFactory,
            @NonNull AgentController.AgentListener listener,
            @NonNull AtomicBoolean cancelFlag,
            @NonNull Handler mainHandler,
            @Nullable AgentPlan currentPlan,
            int executionStartIndex,
            @NonNull Callback callback) {
        this.session = session;
        this.promptBuilder = promptBuilder;
        this.traceManager = traceManager;
        this.clientFactory = clientFactory;
        this.listener = listener;
        this.cancelFlag = cancelFlag;
        this.mainHandler = mainHandler;
        this.currentPlan = currentPlan;
        this.executionStartIndex = executionStartIndex;
        this.callback = callback;
    }

    /**
     * Run the summarize flow. Call on a background thread.
     */
    void summarize() {
        AIConfigProvider config = clientFactory.getConfig();
        String model = config.getModel();
        ProviderAdapter adapter = config.getAdapter();

        LlmHttpClient httpClient = clientFactory.create();

        // Build summarize request
        CharSequence firstMsgText = session.getMessages().isEmpty() ? null
                : session.getMessages().get(0).text;
        String originalPrompt = firstMsgText != null ? firstMsgText.toString() : "";

        // Collect execution results for summarization — only from current round.
        // Messages before executionStartIndex belong to previous rounds.
        List<String[]> results = new ArrayList<>();
        List<AgentMessage> msgs = session.getMessages();
        int startIdx = Math.min(executionStartIndex, msgs.size());
        for (int i = startIdx; i < msgs.size(); i++) {
            AgentMessage msg = msgs.get(i);
            if (msg.type == AgentMessage.Type.EXECUTION_CLI && !msg.terminalLines.isEmpty()) {
                String cmd = msg.terminalLines.get(0);
                StringBuilder output = new StringBuilder();
                for (int j = 1; j < msg.terminalLines.size(); j++) {
                    if (j > 1) output.append('\n');
                    output.append(msg.terminalLines.get(j));
                }
                results.add(new String[]{cmd, output.toString()});
            }
        }

        // Cap total data for small models.
        // Each command capped at 800 chars, total capped at 3000 chars.
        final int MAX_PER_COMMAND = 800;
        final int MAX_TOTAL = 3000;
        List<String[]> cappedResults = new ArrayList<>();
        int totalChars = 0;
        boolean wasTruncated = false;
        for (String[] pair : results) {
            String output = pair[1];
            if (totalChars >= MAX_TOTAL) {
                wasTruncated = true;
                break;
            }
            if (output.length() > MAX_PER_COMMAND) {
                output = output.substring(0, MAX_PER_COMMAND)
                        + "\n... (" + (output.length() - MAX_PER_COMMAND) + " chars truncated)";
                wasTruncated = true;
            }
            int remaining = MAX_TOTAL - totalChars;
            if (output.length() > remaining) {
                output = output.substring(0, remaining) + "\n... (truncated)";
                wasTruncated = true;
            }
            cappedResults.add(new String[]{pair[0], output});
            totalChars += output.length() + pair[0].length() + 10;
        }

        if (wasTruncated) {
            Log.i(TAG, "summarize: data capped to " + totalChars + " chars for small model");
        }

        String summarizePrompt = promptBuilder.buildSummarizePrompt(
                originalPrompt, cappedResults);
        LlmRequest request = new LlmRequest(model);
        request.addSystemMessage(summarizePrompt);
        request.addUserMessage("Summarize the results concisely.");
        request.temperature = 0.3;
        request.maxTokens = 1024;

        traceManager.newTrace("summarize");
        Log.i(TAG, traceManager.formatLogMessage(
                "summarize: calling LLM, results=" + cappedResults.size()
                        + ", totalChars=" + totalChars));

        // For HID mode: no output to summarize, skip LLM call
        final boolean isHidModeFinal = "hid".equals(promptBuilder.getExecutionMode());
        if (isHidModeFinal || cappedResults.isEmpty()) {
            final int totalSteps = currentPlan != null ? currentPlan.steps.size() : 0;
            postToMain(() -> {
                if (listener != null) listener.onSummaryToken("✅ Completed " + totalSteps + " step(s).");
                if (listener != null) listener.onSummaryComplete();
                session.addAssistantMessage("✅ Completed " + totalSteps + " step(s).");
            });
            callback.onSummarizeDone();
            traceManager.endSession(true, null);
            return;
        }

        // Store for client-side fallback
        final List<String[]> finalResults = cappedResults;
        final boolean finalWasTruncated = wasTruncated;

        httpClient.chatStream(request, adapter, new LlmResult() {
            @Override
            public void onChunk(@NonNull LlmResponse chunk) {
                if (!chunk.content.isEmpty()) {
                    postToMain(() -> {
                        if (listener != null) listener.onSummaryToken(chunk.content);
                    });
                }
            }

            @Override
            public void onComplete(@NonNull LlmResponse fullResponse) {
                Log.i(TAG, "summarize: stream complete, length=" + fullResponse.content.length());

                // If streaming returned empty/too-short content, try chatSync
                if (fullResponse.content.length() < 20) {
                    Log.w(TAG, "summarize: streaming returned " + fullResponse.content.length()
                            + " chars, trying chatSync fallback...");
                    try {
                        LlmResponse syncResponse = httpClient.chatSync(request, adapter);
                        Log.i(TAG, "summarize: chatSync returned " + syncResponse.content.length()
                                + " chars");

                        if (syncResponse.content.length() < 20) {
                            // Both methods returned empty — use client-side fallback
                            String fallback = buildClientSideFallback(finalResults, finalWasTruncated);
                            postToMain(() -> {
                                session.addAssistantMessage(fallback);
                                if (listener != null) listener.onSummaryReset();
                                if (listener != null) listener.onSummaryToken(fallback);
                                if (listener != null) listener.onSummaryComplete();
                            });
                        } else {
                            postToMain(() -> {
                                session.addAssistantMessage(syncResponse.content);
                                if (listener != null) listener.onSummaryReset();
                                if (listener != null) listener.onSummaryToken(syncResponse.content);
                                if (listener != null) listener.onSummaryComplete();
                                traceManager.endSession(true, null);
                            });
                        }
                    } catch (Exception syncErr) {
                        Log.e(TAG, "summarize: chatSync also failed", syncErr);
                        String fallback = buildClientSideFallback(finalResults, finalWasTruncated);
                        traceManager.endSession(true, "error_fallback");
                        postToMain(() -> {
                            session.addAssistantMessage(fallback);
                            if (listener != null) listener.onSummaryReset();
                            if (listener != null) listener.onSummaryToken(fallback);
                            if (listener != null) listener.onSummaryComplete();
                        });
                    }
                    callback.onSummarizeDone();
                    return;
                }

                postToMain(() -> {
                    session.addAssistantMessage(fullResponse.content);
                    traceManager.endSession(true, null);
                    if (listener != null) listener.onSummaryComplete();
                });
                callback.onSummarizeDone();
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (cancelFlag.get()) return;
                Log.e(TAG, "summarize: LLM error", e);
                // Use client-side fallback instead of raw error
                String fallback = buildClientSideFallback(finalResults, finalWasTruncated);
                traceManager.endSession(true, "error_fallback");
                postToMain(() -> {
                    session.addAssistantMessage(fallback);
                    if (listener != null) listener.onSummaryReset();
                    if (listener != null) listener.onSummaryToken(fallback);
                    if (listener != null) listener.onSummaryComplete();
                });
                callback.onSummarizeDone();
            }
        }, cancelFlag);
    }

    /**
     * Build a client-side fallback summary when LLM returns empty or fails.
     *
     * <p>Includes:
     * <ul>
     *   <li>Explanation of why LLM summary is unavailable</li>
     *   <li>List of executed commands with first 2 lines of output</li>
     *   <li>Success/failure counts</li>
     * </ul>
     */
    @NonNull
    private String buildClientSideFallback(@NonNull List<String[]> results, boolean wasTruncated) {
        StringBuilder sb = new StringBuilder();

        // Explanation header
        sb.append("⚠️ **Summary unavailable**\n\n");
        sb.append("The AI model returned an empty response. This usually happens when:\n");
        sb.append("- Too many commands were executed (large output)\n");
        sb.append("- The model's context window was exceeded\n");
        sb.append("- Temporary model service issue\n\n");

        if (wasTruncated) {
            sb.append("_Note: Output was truncated to fit model limits._\n\n");
        }

        // Command list
        sb.append("**Executed commands:**\n\n");

        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < results.size(); i++) {
            String cmd = results.get(i)[0];
            String output = results.get(i)[1];

            boolean isError = output.startsWith("Error:")
                    || output.contains("Exec failed")
                    || output.contains("session is down")
                    || output.contains("not captured in HID mode");

            if (isError) failureCount++;
            else successCount++;

            String cmdName = cmd.startsWith("$ ") ? cmd.substring(2) : cmd;
            sb.append("**").append(i + 1).append(". ").append(cmdName).append("**");
            sb.append(isError ? " " : " ✅").append("\n");

            if (!output.isEmpty()) {
                String[] lines = output.split("\n", 3);
                for (String line : lines) {
                    sb.append("> ").append(line).append("\n");
                }
                if (lines.length >= 3) {
                    sb.append("> ...\n");
                }
            }
            sb.append("\n");
        }

        // Footer
        sb.append("---\n");
        sb.append("**Total**: ").append(results.size()).append(" command(s) — ");
        sb.append(successCount).append(" succeeded");
        if (failureCount > 0) {
            sb.append(", ").append(failureCount).append(" failed");
        }
        sb.append(".\n");

        return sb.toString();
    }

    /** Post a runnable to the main thread, or run inline if already on main. */
    private void postToMain(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    /** Callback for state transition when summarize completes. */
    interface Callback {
        void onSummarizeDone();
    }
}
