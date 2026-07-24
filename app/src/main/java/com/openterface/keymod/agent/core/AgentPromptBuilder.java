package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.llm.LlmRequest;

/**
 * Builds LLM prompts for the Agent based on execution mode and user input.
 *
 * <p>Supports three modes:
 * <ul>
 *   <li>{@code terminal} — SSH command execution</li>
 *   <li>{@code hid} — wireless keyboard control</li>
 *   <li>{@code macro} — macro playback</li>
 * </ul>
 *
 * <p>Custom prompts: when a non-empty custom prompt is provided to
 * {@link #buildRequest}, it overrides the default mode-specific template.
 * This allows users to configure prompts via AgentSettingsBottomSheet.
 *
 * <p>TODO: Move prompt strings to string resources for localization (Day 7+).</p>
 */
public final class AgentPromptBuilder {

    private String executionMode = "terminal";

    public AgentPromptBuilder() {
        // No dependencies — prompts are hardcoded for now.
        // Day 7+: accept Context to load localized strings from resources.
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
     *
     * @param model    model name (e.g. "gpt-4o-mini")
     * @param userInput the user's natural language request
     * @return request with system + user messages
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
     *
     * @param model        model name (e.g. "gpt-4o-mini")
     * @param userInput    the user's natural language request
     * @param customPrompt custom system prompt (null or empty to use default)
     * @return request with system + user messages
     */
    @NonNull
    public LlmRequest buildRequest(@NonNull String model, @NonNull String userInput,
                                   @Nullable String customPrompt) {
        LlmRequest request = new LlmRequest(model);

        // Use custom prompt if provided, otherwise use default
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

    // ── Mode-specific prompts ────────────────────────────────────────────

    @NonNull
    private String buildTerminalSystemPrompt() {
        return "You are a system administration assistant. The user will execute commands "
                + "on a remote computer via SSH.\n\n"
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
