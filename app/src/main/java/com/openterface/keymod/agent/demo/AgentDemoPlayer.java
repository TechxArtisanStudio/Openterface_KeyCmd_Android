package com.openterface.keymod.agent.demo;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.ui.AgentMessage;

import java.util.ArrayList;
import java.util.List;

/** Drives a hardcoded marketing demo script with timed reveals and execution animations. */
public final class AgentDemoPlayer {

    public interface Listener {
        void onMessagesUpdated(@NonNull List<AgentMessage> messages);

        void onWaitingForApprove(boolean waiting);

        void onPlaybackComplete();

        void onPlaybackReset();
    }

    public enum PauseAt {
        NONE,
        PLAN,
        ACT
    }

    private static final long BEAT_DELAY_MS = 700L;
    private static final long TERMINAL_LINE_DELAY_MS = 450L;
    private static final long MACRO_STEP_DELAY_MS = 650L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    @Nullable private AgentDemoScript script;
    private final List<AgentMessage> messages = new ArrayList<>();
    private PauseAt pauseAt = PauseAt.ACT;
    private boolean autoApprove;
    private boolean cancelled;

    public AgentDemoPlayer(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void configure(@NonNull PauseAt pauseAt, boolean autoApprove) {
        this.pauseAt = pauseAt;
        this.autoApprove = autoApprove;
    }

    public void start(@NonNull AgentDemoScript script) {
        cancelInternal(false);
        this.script = script;
        messages.clear();
        notifyMessages();
        listener.onWaitingForApprove(false);
        postIntroSequence(0);
    }

    public void approveAndRun() {
        if (script == null || cancelled) {
            return;
        }
        if (!hasActBar()) {
            return;
        }
        listener.onWaitingForApprove(false);
        removeActBar();
        runExecutionPhase();
    }

    public void cancel() {
        cancelInternal(true);
    }

    public void reset() {
        cancelInternal(true);
        script = null;
        messages.clear();
        listener.onPlaybackReset();
        notifyMessages();
        listener.onWaitingForApprove(false);
    }

    private void cancelInternal(boolean notifyReset) {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        if (notifyReset && script != null) {
            listener.onPlaybackReset();
        }
        cancelled = false;
    }

    private void postIntroSequence(int step) {
        if (script == null) {
            return;
        }
        switch (step) {
            case 0:
                messages.add(AgentMessage.user(script.userPrompt));
                notifyMessages();
                handler.postDelayed(() -> postIntroSequence(1), BEAT_DELAY_MS);
                break;
            case 1:
                messages.add(AgentMessage.assistant(script.assistantIntro));
                notifyMessages();
                handler.postDelayed(() -> postIntroSequence(2), BEAT_DELAY_MS);
                break;
            case 2:
                messages.add(AgentMessage.plan(script.planSteps));
                notifyMessages();
                if (pauseAt == PauseAt.PLAN) {
                    listener.onPlaybackComplete();
                    return;
                }
                handler.postDelayed(() -> postIntroSequence(3), BEAT_DELAY_MS);
                break;
            case 3:
                messages.add(AgentMessage.actBar());
                notifyMessages();
                if (pauseAt == PauseAt.ACT && !autoApprove) {
                    listener.onWaitingForApprove(true);
                    return;
                }
                if (autoApprove) {
                    handler.postDelayed(this::runExecutionPhase, BEAT_DELAY_MS);
                }
                break;
            default:
                break;
        }
    }

    private void runExecutionPhase() {
        if (script == null) {
            return;
        }
        removeActBar();
        if (script.hasCliExecution) {
            runCliAnimation(0, new ArrayList<>());
        } else if (script.hasMacroExecution) {
            runMacroAnimation(0);
        } else {
            finishWithSummary();
        }
    }

    private void runCliAnimation(int lineIndex, @NonNull List<String> revealed) {
        if (script == null) {
            return;
        }
        if (lineIndex == 0) {
            upsertExecutionCli(revealed);
        }
        if (lineIndex >= script.terminalOutputLines.size()) {
            if (script.hasMacroExecution) {
                handler.postDelayed(() -> runMacroAnimation(0), BEAT_DELAY_MS);
            } else {
                finishWithSummary();
            }
            return;
        }
        revealed.add(script.terminalOutputLines.get(lineIndex));
        upsertExecutionCli(revealed);
        handler.postDelayed(
                () -> runCliAnimation(lineIndex + 1, revealed),
                TERMINAL_LINE_DELAY_MS);
    }

    private void runMacroAnimation(int stepIndex) {
        if (script == null) {
            return;
        }
        int total = Math.max(1, script.macroChecklist.size());
        int progress = Math.min(100, (int) (((stepIndex + 1) / (float) total) * 100f));
        String chip = stepIndex < script.macroStatusChips.size()
                ? script.macroStatusChips.get(stepIndex)
                : null;
        upsertExecutionMacro(progress, stepIndex, chip);
        if (stepIndex + 1 >= script.macroChecklist.size()) {
            handler.postDelayed(this::finishWithSummary, BEAT_DELAY_MS);
            return;
        }
        handler.postDelayed(() -> runMacroAnimation(stepIndex + 1), MACRO_STEP_DELAY_MS);
    }

    private void finishWithSummary() {
        if (script == null) {
            return;
        }
        removeExecutionMessages();
        messages.add(AgentMessage.assistant(script.summaryMessage));
        notifyMessages();
        listener.onPlaybackComplete();
    }

    private void upsertExecutionCli(@NonNull List<String> lines) {
        removeExecutionMessages();
        messages.add(AgentMessage.executionCli(lines));
        notifyMessages();
    }

    private void upsertExecutionMacro(int progress, int currentStep, @Nullable String chip) {
        if (script == null) {
            return;
        }
        removeExecutionMessages();
        messages.add(AgentMessage.executionMacro(
                script.macroChecklist,
                progress,
                currentStep,
                chip));
        notifyMessages();
    }

    private void removeActBar() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).type == AgentMessage.Type.ACT_BAR) {
                messages.remove(i);
                notifyMessages();
                return;
            }
        }
    }

    private void removeExecutionMessages() {
        boolean changed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage.Type type = messages.get(i).type;
            if (type == AgentMessage.Type.EXECUTION_CLI || type == AgentMessage.Type.EXECUTION_MACRO) {
                messages.remove(i);
                changed = true;
            }
        }
        if (changed) {
            notifyMessages();
        }
    }

    private boolean hasActBar() {
        for (AgentMessage message : messages) {
            if (message.type == AgentMessage.Type.ACT_BAR) {
                return true;
            }
        }
        return false;
    }

    private void notifyMessages() {
        listener.onMessagesUpdated(new ArrayList<>(messages));
    }
}
