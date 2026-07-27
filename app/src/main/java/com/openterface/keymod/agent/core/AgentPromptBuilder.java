package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.llm.LlmRequest;

/**
 * Builds LLM prompts for the Agent based on execution mode, target OS and user input.
 *
 * <p>Supports three modes:
 * <ul>
 *   <li>{@code terminal} — SSH command execution (OS-aware)</li>
 *   <li>{@code hid} — wireless keyboard control</li>
 *   <li>{@code macro} — macro playback</li>
 * </ul>
 *
 * <p>Custom prompts: when a non-empty custom prompt is provided to
 * {@link #buildRequest}, it overrides the default mode-specific template.
 * This allows users to configure prompts via AgentSettingsBottomSheet.
 */
public final class AgentPromptBuilder {

    /** SharedPreferences name for Agent-specific preferences. */
    private static final String AGENT_PREFS_NAME = "agent_prefs";
    /** Agent-specific target OS (independent from global PREF_TARGET_OS). */
    private static final String PREF_AGENT_TARGET_OS = "agent_target_os";

    private final Context context;
    private String executionMode = "terminal";

    public AgentPromptBuilder(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Set the execution mode: {@code "terminal"} | {@code "hid"} | {@code "macro"} */
    public void setExecutionMode(@NonNull String mode) {
        this.executionMode = mode;
    }

    @NonNull
    public String getExecutionMode() {
        return executionMode;
    }

    /** Build the system prompt for the current execution mode */
    @NonNull
    public String buildSystemPrompt() {
        switch (executionMode) {
            case "hid":
                return buildHidSystemPrompt();
            case "macro":
                return buildMacroSystemPrompt();
            case "terminal":
            default:
                return buildTerminalSystemPrompt();
        }
    }

    /** Build the user prompt combining instructions with user input */
    @NonNull
    public String buildUserPrompt(@NonNull String userInput) {
        return userInput;
    }

    /**
     * Build a complete {@link LlmRequest} ready to send to the LLM.
     * Uses the default mode-specific system prompt.
     */
    @NonNull
    public LlmRequest buildRequest(@NonNull String model, @NonNull String userInput) {
        return buildRequest(model, userInput, null);
    }

    /**
     * Build a complete {@link LlmRequest} with optional custom system prompt.
     *
     * <p>When {@code customPrompt} is non-null and non-empty, it replaces
     * the default mode-specific system prompt entirely.
     */
    @NonNull
    public LlmRequest buildRequest(@NonNull String model, @NonNull String userInput,
                                   @Nullable String customPrompt) {
        LlmRequest request = new LlmRequest(model);

        String systemPrompt;
        if (customPrompt != null && !customPrompt.isEmpty()) {
            systemPrompt = customPrompt;
        } else {
            systemPrompt = buildSystemPrompt();
        }

        request.addSystemMessage(systemPrompt);
        request.addUserMessage(buildUserPrompt(userInput));
        request.temperature = 0.3;  // Lower temperature for structured output
        request.maxTokens = 2048;
        return request;
    }

    // ── Target OS ────────────────────────────────────────────────────────

    /** Read the global target OS (mirrored from the active profile or picked in sheet). */
    @NonNull
    private String getTargetOs() {
        SharedPreferences prefs = context.getSharedPreferences(AGENT_PREFS_NAME, Context.MODE_PRIVATE);
        String os = prefs.getString(PREF_AGENT_TARGET_OS, "macos");
        return os != null ? os : "macos";
    }

    @NonNull
    private String osDisplayName(@NonNull String os) {
        switch (os) {
            case "windows": return "Windows";
            case "linux":   return "Linux";
            default:        return "macOS";
        }
    }

    // ── Mode-specific prompts ────────────────────────────────────────────

    @NonNull
    private String buildTerminalSystemPrompt() {
        String osName = osDisplayName(getTargetOs());
        return "You are a system administration assistant. "
                + "The target system is " + osName + ". "
                + "Use commands appropriate for " + osName + ".\n\n"
                + "Generate an execution plan based on the user's request.\n\n"
                + "## Output Format\n"
                + "Return JSON:\n"
                + "{\n"
                + "  \"summary\": \"one-line summary of what you will do\",\n"
                + "  \"steps\": [\n"
                + "    {\"title\": \"step title\", \"command\": \"bash command\", \"kind\": \"terminal\"}\n"
                + "  ]\n"
                + "}\n\n"
                + "## Rules\n"
                + "- Each step must be a safe command\n"
                + "- Do NOT use destructive commands like 'rm -rf /'\n"
                + "- If a command might fail, provide fallback options\n"
                + "- Return ONLY valid JSON, no additional text";
    }

    @NonNull
    private String buildHidSystemPrompt() {
        return "You are a keyboard control assistant. The user controls a target computer "
                + "through a wireless keyboard.\n\n"
                + "## Output Format\n"
                + "Return JSON:\n"
                + "{\n"
                + "  \"summary\": \"one-line summary\",\n"
                + "  \"steps\": [\n"
                + "    {\"title\": \"step title\", \"keys\": \"<CMD>s</CMD>\", \"kind\": \"hid\"}\n"
                + "  ]\n"
                + "}\n\n"
                + "## Key Syntax\n"
                + "- Modifier keys: <CMD>, <CTRL>, <ALT>, <SHIFT>\n"
                + "- Wrap key in tags: <CMD>s</CMD> means Cmd+S\n"
                + "- Special keys: <ENTER>, <TAB>, <ESC>, <BACKSPACE>, <DELETE>\n"
                + "- Arrow keys: <UP>, <DOWN>, <LEFT>, <RIGHT>\n"
                + "- Plain text: just type the characters\n\n"
                + "## Rules\n"
                + "- Each step should be a distinct keyboard action\n"
                + "- Return ONLY valid JSON, no additional text";
    }

    @NonNull
    private String buildMacroSystemPrompt() {
        return "You are a macro orchestration assistant. The user wants to execute recorded "
                + "macros on the target device.\n\n"
                + "## Output Format\n"
                + "Return JSON:\n"
                + "{\n"
                + "  \"summary\": \"one-line summary\",\n"
                + "  \"steps\": [\n"
                + "    {\"title\": \"step title\", \"macroId\": \"macro_name_or_id\", \"kind\": \"macro\"}\n"
                + "  ]\n"
                + "}\n\n"
                + "## Rules\n"
                + "- Reference macros by their name or ID\n"
                + "- Order steps logically\n"
                + "- Return ONLY valid JSON, no additional text";
    }
}
