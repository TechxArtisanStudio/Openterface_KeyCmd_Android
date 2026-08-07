package com.openterface.keymod.agent.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One row in the Agent chat transcript (marketing demo). */
public final class AgentMessage {

    public enum Type {
        USER,
        ASSISTANT,
        PLAN,
        ACT_BAR,
        EXECUTION_CLI,
        EXECUTION_MACRO,
        THINKING
    }

    @NonNull public final Type type;
    @Nullable public final CharSequence text;
    @NonNull public final List<AgentPlanStep> planSteps;
    @NonNull public final List<String> terminalLines;
    @NonNull public final List<String> macroSteps;
    public final int macroProgress;
    public final int macroCurrentStep;
    @Nullable public final String macroStatusChip;
    public final boolean isHidMode;
    /** True when an EXECUTION_CLI command has finished (success or failure). */
    public final boolean isComplete;
    /** True when this message represents an error (red error bubble). */
    public final boolean isError;
    /** True when the error message has a Retry button. */
    public final boolean canRetry;
    /** True when an EXECUTION_CLI command succeeded (false = failed, null = running). */
    @Nullable public final Boolean cliSuccess;

    private AgentMessage(
            @NonNull Type type,
            @Nullable CharSequence text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip,
            boolean isHidMode,
            boolean isComplete) {
        this(type, text, planSteps, terminalLines, macroSteps, macroProgress, macroCurrentStep,
                macroStatusChip, isHidMode, isComplete, false, false, null);
    }

    private AgentMessage(
            @NonNull Type type,
            @Nullable CharSequence text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip,
            boolean isHidMode,
            boolean isComplete,
            boolean isError,
            boolean canRetry) {
        this(type, text, planSteps, terminalLines, macroSteps, macroProgress, macroCurrentStep,
                macroStatusChip, isHidMode, isComplete, isError, canRetry, null);
    }

    private AgentMessage(
            @NonNull Type type,
            @Nullable CharSequence text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip,
            boolean isHidMode,
            boolean isComplete,
            boolean isError,
            boolean canRetry,
            @Nullable Boolean cliSuccess) {
        this.type = type;
        this.text = text;
        this.planSteps = planSteps;
        this.terminalLines = terminalLines;
        this.macroSteps = macroSteps;
        this.macroProgress = macroProgress;
        this.macroCurrentStep = macroCurrentStep;
        this.macroStatusChip = macroStatusChip;
        this.isHidMode = isHidMode;
        this.isComplete = isComplete;
        this.isError = isError;
        this.canRetry = canRetry;
        this.cliSuccess = cliSuccess;
    }

    private AgentMessage(
            @NonNull Type type,
            @Nullable CharSequence text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip) {
        this(type, text, planSteps, terminalLines, macroSteps, macroProgress, macroCurrentStep,
                macroStatusChip, false, false, false, false, null);
    }

    @NonNull
    public static AgentMessage user(@NonNull CharSequence text) {
        return new AgentMessage(Type.USER, text, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    public static AgentMessage assistant(@NonNull CharSequence text) {
        return new AgentMessage(Type.ASSISTANT, text, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    /**
     * Create an error assistant message with optional Retry button.
     * Rendered as a red error bubble with a warning icon. If {@code canRetry}
     * is true, a Retry button is shown below the bubble.
     * Accepts {@link CharSequence} to support styled text (e.g. SpannableString with inline icons).
     */
    @NonNull
    public static AgentMessage assistantError(@NonNull CharSequence text, boolean canRetry) {
        return new AgentMessage(Type.ASSISTANT, text, emptySteps(), emptyLines(),
                emptyLines(), 0, 0, null, false, false, true, canRetry);
    }

    @NonNull
    public static AgentMessage plan(@NonNull List<AgentPlanStep> steps) {
        return new AgentMessage(Type.PLAN, null, new ArrayList<>(steps), emptyLines(), emptyLines(), 0, 0, null, false, false);
    }

    @NonNull
    public static AgentMessage plan(@NonNull List<AgentPlanStep> steps, boolean isHidMode) {
        return new AgentMessage(Type.PLAN, null, new ArrayList<>(steps), emptyLines(), emptyLines(), 0, 0, null, isHidMode, false);
    }

    @NonNull
    public static AgentMessage actBar() {
        return new AgentMessage(Type.ACT_BAR, null, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    public static AgentMessage executionCli(@NonNull List<String> lines) {
        return new AgentMessage(Type.EXECUTION_CLI, null, emptySteps(), new ArrayList<>(lines), emptyLines(), 0, 0, null, false, false);
    }

    /** Create a completed EXECUTION_CLI message (status shows "完成" instead of "Running…"). */
    @NonNull
    public static AgentMessage executionCliComplete(@NonNull List<String> lines, boolean success) {
        return new AgentMessage(Type.EXECUTION_CLI, null, emptySteps(), new ArrayList<>(lines),
                emptyLines(), 0, 0, null, false, true, false, false, success);
    }

    /** @deprecated Use {@link #executionCliComplete(List, boolean)} instead. */
    @Deprecated
    @NonNull
    public static AgentMessage executionCliComplete(@NonNull List<String> lines) {
        return executionCliComplete(lines, true);
    }

    @NonNull
    public static AgentMessage executionMacro(
            @NonNull List<String> steps,
            int progress,
            int currentStep,
            @Nullable String statusChip) {
        return new AgentMessage(
                Type.EXECUTION_MACRO,
                null,
                emptySteps(),
                emptyLines(),
                new ArrayList<>(steps),
                progress,
                currentStep,
                statusChip);
    }

    @NonNull
    public static AgentMessage thinking(@NonNull CharSequence text) {
        return new AgentMessage(Type.THINKING, text, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    private static List<AgentPlanStep> emptySteps() {
        return Collections.emptyList();
    }

    @NonNull
    private static List<String> emptyLines() {
        return Collections.emptyList();
    }
}
