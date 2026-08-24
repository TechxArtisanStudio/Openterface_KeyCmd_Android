package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizes Agent plans for HID mode execution.
 *
 * <p>Implements the "ponytail safety net": when the Agent is in HID mode and the LLM
 * generates a plan with {@code terminal} steps but no preceding {@code hid} step to open
 * a terminal app, this class automatically injects the appropriate OS-specific
 * terminal-launch HID step at the beginning of the plan.</p>
 *
 * <p>Injection condition (all three must be true):
 * <ol>
 *   <li>Execution mode is HID (no SSH profile)</li>
 *   <li>Plan contains at least one {@code terminal} step</li>
 *   <li>No {@code hid} step exists before the first {@code terminal} step</li>
 * </ol>
 *
 * <p>OS-specific terminal launch payloads (from iOS spec, fixed):
 * <ul>
 *   <li>macOS: {@code <CMD><SPACE></CMD><DELAY1S><CMD>a</CMD><BACK><DELAY1S>terminal<ENTER><DELAY3S>}
 *   <li>Linux: {@code <CTRL><ALT>t<DELAY3S>}
 *   <li>Windows: {@code <WIN>r<DELAY1S>cmd<ENTER><DELAY4S>}
 * </ul>
 */
public final class PlanSanitizer {

    /** macOS: Cmd+Space (Spotlight) → clear → type "terminal" → Enter → wait 3s */
    private static final String LAUNCH_TERM_MACOS =
            "<CMD><SPACE></CMD><DELAY1S><CMD>a</CMD><BACK><DELAY1S>terminal<ENTER><DELAY3S>";

    /** Linux: Ctrl+Alt+T → wait 3s */
    private static final String LAUNCH_TERM_LINUX =
            "<CTRL><ALT>t<DELAY3S>";

    /** Windows: Win+R → type "cmd" → Enter → wait 4s */
    private static final String LAUNCH_TERM_WINDOWS =
            "<WIN>r<DELAY1S>cmd<ENTER><DELAY4S>";

    private PlanSanitizer() {} // static utility

    /**
     * Sanitize a plan for HID mode. Injects a terminal-launch HID step if needed.
     *
     * @param plan     the original plan from the LLM
     * @param targetOS the target OS name: "macos", "linux", or "windows"
     * @return sanitized plan (may be the same instance if no injection needed)
     */
    @NonNull
    public static AgentPlan sanitizeForHid(@NonNull AgentPlan plan, @NonNull String targetOS) {
        if (shouldInject(plan)) {
            String payload = getLaunchPayload(targetOS);
            AgentPlan.Step injectStep = AgentPlan.Step.hid(
                    0,
                    getLaunchTitle(targetOS),
                    payload);

            // Rebuild steps with injected step at index 0, re-indexing the rest
            List<AgentPlan.Step> newSteps = new ArrayList<>(plan.steps.size() + 1);
            newSteps.add(injectStep);
            for (int i = 0; i < plan.steps.size(); i++) {
                AgentPlan.Step old = plan.steps.get(i);
                newSteps.add(new AgentPlan.Step(
                        i + 1, old.title, old.command, old.keys, old.macroId, old.kind));
            }

            String newSummary = plan.summary + " (auto-injected terminal launch for HID mode)";
            return new AgentPlan(newSummary, newSteps);
        }
        return plan;
    }

    /**
     * Determine whether a terminal-launch HID step should be injected.
     * Returns true when:
     * <ul>
     *   <li>Plan has at least one {@code terminal} step, AND</li>
     *   <li>No {@code hid} step exists before the first {@code terminal} step</li>
     * </ul>
     */
    static boolean shouldInject(@NonNull AgentPlan plan) {
        boolean foundHidBeforeTerminal = false;
        boolean foundTerminal = false;

        for (AgentPlan.Step step : plan.steps) {
            String kind = step.kind;
            if ("hid".equals(kind)) {
                foundHidBeforeTerminal = true;
                break; // HID before any terminal → no injection needed
            }
            if ("terminal".equals(kind)) {
                foundTerminal = true;
                break; // Terminal found without preceding HID → inject
            }
        }

        return foundTerminal && !foundHidBeforeTerminal;
    }

    /**
     * Get the OS-specific HID payload to launch a terminal app.
     */
    @NonNull
    static String getLaunchPayload(@NonNull String targetOS) {
        switch (targetOS.toLowerCase()) {
            case "windows":
                return LAUNCH_TERM_WINDOWS;
            case "linux":
                return LAUNCH_TERM_LINUX;
            case "macos":
            default:
                return LAUNCH_TERM_MACOS;
        }
    }

    /**
     * Get a human-readable title for the terminal-launch step.
     */
    @NonNull
    static String getLaunchTitle(@NonNull String targetOS) {
        switch (targetOS.toLowerCase()) {
            case "windows":
                return "Open Command Prompt via Win+R";
            case "linux":
                return "Open Terminal via Ctrl+Alt+T";
            case "macos":
            default:
                return "Open Terminal via Spotlight";
        }
    }
}
