package com.openterface.keymod.agent.demo;

import androidx.annotation.NonNull;

import com.openterface.keymod.agent.ui.AgentPlanStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Curated marketing demo script definition. */
public final class AgentDemoScript {

    public static final String ID_HERO_MIXED = "hero_mixed";
    public static final String ID_CLI_ONLY = "cli_only";
    public static final String ID_MACRO_ONLY = "macro_only";

    @NonNull public final String id;
    @NonNull public final String pickerTitle;
    @NonNull public final String pickerTagline;
    @NonNull public final String userPrompt;
    @NonNull public final String assistantIntro;
    @NonNull public final List<AgentPlanStep> planSteps;
    @NonNull public final String summaryMessage;
    @NonNull public final List<String> terminalOutputLines;
    @NonNull public final List<String> macroChecklist;
    @NonNull public final List<String> macroStatusChips;
    public final boolean hasCliExecution;
    public final boolean hasMacroExecution;

    public AgentDemoScript(
            @NonNull String id,
            @NonNull String pickerTitle,
            @NonNull String pickerTagline,
            @NonNull String userPrompt,
            @NonNull String assistantIntro,
            @NonNull List<AgentPlanStep> planSteps,
            @NonNull String summaryMessage,
            @NonNull List<String> terminalOutputLines,
            @NonNull List<String> macroChecklist,
            @NonNull List<String> macroStatusChips,
            boolean hasCliExecution,
            boolean hasMacroExecution) {
        this.id = id;
        this.pickerTitle = pickerTitle;
        this.pickerTagline = pickerTagline;
        this.userPrompt = userPrompt;
        this.assistantIntro = assistantIntro;
        this.planSteps = planSteps;
        this.summaryMessage = summaryMessage;
        this.terminalOutputLines = terminalOutputLines;
        this.macroChecklist = macroChecklist;
        this.macroStatusChips = macroStatusChips;
        this.hasCliExecution = hasCliExecution;
        this.hasMacroExecution = hasMacroExecution;
    }

    @NonNull
    static List<String> lines(String... lines) {
        return Collections.unmodifiableList(Arrays.asList(lines));
    }
}
