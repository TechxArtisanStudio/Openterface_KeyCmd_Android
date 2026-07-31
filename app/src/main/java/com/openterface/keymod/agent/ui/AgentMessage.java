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
    @Nullable public final String text;
    @NonNull public final List<AgentPlanStep> planSteps;
    @NonNull public final List<String> terminalLines;
    @NonNull public final List<String> macroSteps;
    public final int macroProgress;
    public final int macroCurrentStep;
    @Nullable public final String macroStatusChip;
    public final boolean isHidMode;

    private AgentMessage(
            @NonNull Type type,
            @Nullable String text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip,
            boolean isHidMode) {
        this.type = type;
        this.text = text;
        this.planSteps = planSteps;
        this.terminalLines = terminalLines;
        this.macroSteps = macroSteps;
        this.macroProgress = macroProgress;
        this.macroCurrentStep = macroCurrentStep;
        this.macroStatusChip = macroStatusChip;
        this.isHidMode = isHidMode;
    }

    private AgentMessage(
            @NonNull Type type,
            @Nullable String text,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull List<String> terminalLines,
            @NonNull List<String> macroSteps,
            int macroProgress,
            int macroCurrentStep,
            @Nullable String macroStatusChip) {
        this(type, text, planSteps, terminalLines, macroSteps, macroProgress, macroCurrentStep, macroStatusChip, false);
    }

    @NonNull
    public static AgentMessage user(@NonNull String text) {
        return new AgentMessage(Type.USER, text, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    public static AgentMessage assistant(@NonNull String text) {
        return new AgentMessage(Type.ASSISTANT, text, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    public static AgentMessage plan(@NonNull List<AgentPlanStep> steps) {
        return new AgentMessage(Type.PLAN, null, new ArrayList<>(steps), emptyLines(), emptyLines(), 0, 0, null, false);
    }

    @NonNull
    public static AgentMessage plan(@NonNull List<AgentPlanStep> steps, boolean isHidMode) {
        return new AgentMessage(Type.PLAN, null, new ArrayList<>(steps), emptyLines(), emptyLines(), 0, 0, null, isHidMode);
    }

    @NonNull
    public static AgentMessage actBar() {
        return new AgentMessage(Type.ACT_BAR, null, emptySteps(), emptyLines(), emptyLines(), 0, 0, null);
    }

    @NonNull
    public static AgentMessage executionCli(@NonNull List<String> lines) {
        return new AgentMessage(Type.EXECUTION_CLI, null, emptySteps(), new ArrayList<>(lines), emptyLines(), 0, 0, null);
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
    public static AgentMessage thinking(@NonNull String text) {
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
