package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.MacrosManager;
import com.openterface.keymod.agent.llm.LlmRequest;
import com.openterface.terminal.CredentialProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds LLM prompts for the Agent based on execution mode, target OS and user input.
 *
 * <p>Prompt loading priority:
 * <ol>
 *   <li>User-edited prompt from SharedPreferences (if non-empty)</li>
 *   <li>Bundled .md file from assets/Prompts/</li>
 *   <li>Hardcoded fallback string (never null)</li>
 * </ol>
 *
 * <p>Placeholders replaced at build time:
 * <ul>
 *   <li>{@code {{TERMINAL_MODE_CONTEXT}}} — SSH/HID mode context with target info</li>
 *   <li>{@code {{MACRO_CONTEXT}}} — list of available macros</li>
 * </ul>
 *
 * <p>Supports three execution modes:
 * <ul>
 *   <li>{@code terminal} — SSH command execution (OS-aware)</li>
 *   <li>{@code hid} — wireless keyboard control</li>
 *   <li>{@code macro} — macro playback</li>
 * </ul>
 */
public final class AgentPromptBuilder {

    private static final String TAG = "AgentPromptBuilder";

    /** SharedPreferences name for Agent-specific preferences. */
    private static final String AGENT_PREFS_NAME = "agent_prefs";
    /** Agent-specific target OS (independent from global PREF_TARGET_OS). */
    private static final String PREF_AGENT_TARGET_OS = "agent_target_os";

    /** Role IDs for user-editable prompts. */
    static final String ROLE_TERMINAL = "agent_planner_terminal";
    static final String ROLE_HID = "agent_planner_hid";
    static final String ROLE_BASE = "agent_planner";

    private final Context context;
    private String executionMode = "terminal";
    @Nullable private CredentialProfile activeProfile;

    /** Hardcoded fallback prompts — used only when assets are unavailable. */
    private static final String FALLBACK_TERMINAL =
            "You are an autonomous agent that executes commands on a remote device via SSH terminal.\n\n"
            + "{{TERMINAL_MODE_CONTEXT}}\n\n"
            + "Break the user's request into concrete shell commands. Output a JSON plan.\n\n"
            + "## Step types\n\n"
            + "| kind | payload | Purpose |\n"
            + "|---|---|---|\n"
            + "| `terminal` | shell command | Execute via SSH, output is captured |\n\n"
            + "Use only `terminal` steps. Do NOT use `hid` or `macro` steps in terminal mode.\n\n"
            + "## Response format\n\n"
            + "Respond with a single JSON object inside a ```json code fence. No prose before or after.\n\n"
            + "```json\n"
            + "{\n"
            + "  \"intro\": \"One-sentence description.\",\n"
            + "  \"steps\": [\n"
            + "    {\"kind\": \"terminal\", \"title\": \"Run command\", \"payload\": \"uname -a\"}\n"
            + "  ]\n"
            + "}\n"
            + "```\n\n"
            + "## Constraints\n\n"
            + "- Keep steps minimal — one command per step.\n"
            + "- Do not include destructive commands unless explicitly requested.\n"
            + "- Use commands appropriate for the target OS specified above.\n"
            + "- If you cannot fulfill the request, say so in the intro and return empty steps array.";

    private static final String FALLBACK_HID =
            "You are an autonomous agent that controls a computer via BLE keyboard (HID). "
            + "No SSH terminal is available — commands are typed into the active window.\n\n"
            + "{{TERMINAL_MODE_CONTEXT}}\n\n"
            + "Break the user's request into steps. Output a JSON plan.\n\n"
            + "## Step types\n\n"
            + "| kind | payload | Purpose |\n"
            + "|---|---|---|\n"
            + "| `hid` | keyboard tokens | Send keystrokes, shortcuts, typed text |\n"
            + "| `terminal` | shell command | Type command + Enter (output NOT captured) |\n\n"
            + "Your plan MUST have an `hid` step FIRST to open a terminal app, then `terminal` steps for commands.\n\n"
            + "## Keyboard tokens\n\n"
            + "| Action | Syntax |\n"
            + "|---|---|\n"
            + "| Type text | literal characters |\n"
            + "| Modifier held | `<CMD>c</CMD>`, `<CTRL>s</CTRL>`, `<SHIFT>A</SHIFT>` |\n"
            + "| Chords | `<CTRL><SHIFT>t</SHIFT></CTRL>` |\n"
            + "| Special keys | `<ESC>`, `<ENTER>`, `<BACK>`, `<SPACE>`, `<TAB>`, `<F1>`–`<F12>` |\n"
            + "| Arrows | `<LEFT>`, `<RIGHT>`, `<UP>`, `<DOWN>` |\n"
            + "| Delays | `<DELAY1S>` through `<DELAY10S>` |\n\n"
            + "**IMPORTANT:** Always close modifier tags (`</CMD>`) before typing plain text.\n\n"
            + "## Open terminal (HID step)\n\n"
            + "| OS | HID payload |\n"
            + "|---|---|\n"
            + "| macOS | `<CMD><SPACE></CMD><DELAY1S>terminal<ENTER><DELAY3S>` |\n"
            + "| Linux | `<CTRL><ALT>t<DELAY3S>` |\n"
            + "| Windows | `<WIN>r<DELAY1S>cmd<ENTER><DELAY4S>` |\n\n"
            + "## Response format\n\n"
            + "Respond with a single JSON object inside a ```json code fence. No prose.\n\n"
            + "```json\n"
            + "{\n"
            + "  \"intro\": \"One-sentence description.\",\n"
            + "  \"steps\": [\n"
            + "    {\"kind\": \"hid\", \"title\": \"Open Terminal\", \"payload\": \"<CTRL><ALT>t<DELAY3S>\"},\n"
            + "    {\"kind\": \"terminal\", \"title\": \"Check version\", \"payload\": \"uname -a\"}\n"
            + "  ]\n"
            + "}\n"
            + "```\n\n"
            + "## Constraints\n\n"
            + "- First step MUST be `hid` to open terminal.\n"
            + "- Keep steps minimal.\n"
            + "- Since output is NOT captured, avoid commands that depend on reading previous output.";

    private static final String FALLBACK_BASE =
            "You are an autonomous agent that helps users by generating executable plans.\n\n"
            + "{{TERMINAL_MODE_CONTEXT}}\n\n"
            + "{{MACRO_CONTEXT}}\n\n"
            + "Break the user's request into concrete steps. Output a JSON plan.\n\n"
            + "## Step types\n\n"
            + "| kind | payload | Purpose |\n"
            + "|---|---|---|\n"
            + "| `terminal` | shell command | Execute via SSH, output is captured |\n"
            + "| `hid` | keyboard tokens | Send keystrokes to the active window |\n"
            + "| `macro` | macro ID or name | Play a recorded macro sequence |\n\n"
            + "## Response format\n\n"
            + "Respond with a single JSON object inside a ```json code fence. No prose before or after.\n\n"
            + "```json\n"
            + "{\n"
            + "  \"intro\": \"One-sentence description.\",\n"
            + "  \"steps\": [\n"
            + "    {\"kind\": \"terminal\", \"title\": \"Check disk\", \"payload\": \"df -h\"}\n"
            + "  ]\n"
            + "}\n"
            + "```\n\n"
            + "## Constraints\n\n"
            + "- Keep steps minimal — one command per step.\n"
            + "- Do not include destructive commands unless explicitly requested.\n"
            + "- If you cannot fulfill the request, say so in the intro and return empty steps array.";

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

    /** Set the active SSH profile for context injection. */
    public void setActiveProfile(@Nullable CredentialProfile profile) {
        this.activeProfile = profile;
    }

    /** Build the system prompt for the current execution mode. */
    @NonNull
    public String buildSystemPrompt() {
        String roleId;
        String fallback;
        switch (executionMode) {
            case "hid":
                roleId = ROLE_HID;
                fallback = FALLBACK_HID;
                break;
            case "macro":
                roleId = ROLE_BASE;
                fallback = FALLBACK_BASE;
                break;
            case "terminal":
            default:
                roleId = ROLE_TERMINAL;
                fallback = FALLBACK_TERMINAL;
                break;
        }

        // Priority: user edit > assets file > hardcoded fallback
        String template = loadPrompt(roleId, fallback);
        return replacePlaceholders(template);
    }

    /** Build the user prompt combining instructions with user input. */
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
     * the default mode-specific system prompt entirely (user override from settings).
     */
    @NonNull
    public LlmRequest buildRequest(@NonNull String model, @NonNull String userInput,
                                   @Nullable String customPrompt) {
        LlmRequest request = new LlmRequest(model);

        String systemPrompt;
        if (customPrompt != null && !customPrompt.isEmpty()) {
            // User-edited prompt: still replace placeholders
            systemPrompt = replacePlaceholders(customPrompt);
        } else {
            systemPrompt = buildSystemPrompt();
        }

        request.addSystemMessage(systemPrompt);
        request.addUserMessage(buildUserPrompt(userInput));
        request.temperature = 0.3;
        request.maxTokens = 2048;
        return request;
    }

    // ── Retry / Summarize prompts ────────────────────────────────────────

    /**
     * Build a retry prompt after step failures.
     *
     * @param originalPrompt the original user request
     * @param failedSteps pairs of (command/description, error message)
     * @param targetOS the target OS name (e.g. "Linux", "macOS")
     * @return retry system prompt text
     */
    @NonNull
    public String buildRetryPrompt(@NonNull String originalPrompt,
                                    @NonNull List<String[]> failedSteps,
                                    @NonNull String targetOS) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an autonomous agent that executes commands on a ")
          .append(targetOS).append(" device.\n")
          .append("The user asked a question, but some commands failed. ")
          .append("Your job is to provide **alternative** commands that work on ")
          .append(targetOS).append(".\n")
          .append("Use only commands that are correct for ").append(targetOS)
          .append(". Do NOT reuse the failed commands.\n\n")
          .append("The original request was:\n")
          .append(originalPrompt).append("\n\n")
          .append("The following commands failed on ").append(targetOS).append(":\n");

        for (String[] pair : failedSteps) {
            sb.append("Command: ").append(pair[0]).append("\n")
              .append("Error: ").append(pair[1]).append("\n\n");
        }

        sb.append("Please provide alternative commands that will work on ")
          .append(targetOS).append(".\n")
          .append("Respond with a single JSON object inside a ```json code fence. No prose before or after.\n\n")
          .append("```json\n")
          .append("{\n")
          .append("  \"intro\": \"Retrying with alternative commands...\",\n")
          .append("  \"steps\": [\n")
          .append("    {\"kind\": \"terminal\", \"title\": \"Alternative command\", \"payload\": \"correct command\"}\n")
          .append("  ]\n")
          .append("}\n```");

        return sb.toString();
    }

    /**
     * Build a summarize prompt after execution completes.
     *
     * @param originalPrompt the original user request
     * @param results pairs of (command, output)
     * @return summarize system prompt text
     */
    @NonNull
    public String buildSummarizePrompt(@NonNull String originalPrompt,
                                        @NonNull List<String[]> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the results of the following commands for the user.\n\n")
          .append("Original request: ").append(originalPrompt).append("\n\n")
          .append("Execution results:\n");

        for (String[] pair : results) {
            sb.append("$ ").append(pair[0]).append("\n")
              .append(pair[1]).append("\n\n");
        }

        sb.append("Provide a concise, human-readable summary in markdown format.\n")
          .append("Focus on what was accomplished and any important findings.");

        return sb.toString();
    }

    // ── Placeholder replacement ──────────────────────────────────────────

    /**
     * Replace {@code {{TERMINAL_MODE_CONTEXT}} and {@code {{MACRO_CONTEXT}}}
     * placeholders in the prompt template.
     */
    @NonNull
    private String replacePlaceholders(@NonNull String template) {
        String result = template.replace("{{TERMINAL_MODE_CONTEXT}}", buildTerminalModeContext());
        result = result.replace("{{MACRO_CONTEXT}}", buildMacroContext());
        return result;
    }

    /**
     * Build the TERMINAL_MODE_CONTEXT string based on the current execution mode
     * and active SSH profile.
     *
     * <p>In SSH mode: shows connection target, OS, max steps.
     * <p>In HID mode: explains that no SSH is available and terminal must be opened via HID.
     */
    @NonNull
    String buildTerminalModeContext() {
        SharedPreferences prefs = context.getSharedPreferences(AGENT_PREFS_NAME, Context.MODE_PRIVATE);
        int maxSteps = prefs.getInt("agent_max_steps", 10);
        String osName = osDisplayName(getTargetOs());

        if ("hid".equals(executionMode) || activeProfile == null) {
            // HID mode or no SSH profile — no SSH terminal available
            return "**Execution mode: HID (BLE keyboard)**\n"
                    + "Target OS: " + osName + "\n"
                    + "Max steps: " + maxSteps + " (do NOT exceed this; use the fewest steps possible)\n\n"
                    + "No terminal profile is configured. Terminal commands will be **typed "
                    + "into the active window** on the target device via BLE keyboard — nothing is captured.\n"
                    + "You MUST include an `hid` step first to open a terminal app, with a delay to let it launch:\n"
                    + "- macOS: `<CMD><SPACE></CMD><DELAY1S>terminal<ENTER><DELAY3S>`\n"
                    + "- Linux: `<CTRL><ALT>t<DELAY3S>`\n"
                    + "- Windows: `<WIN>r<DELAY1S>cmd<ENTER><DELAY4S>`\n"
                    + "IMPORTANT: always include the closing modifier tag (e.g. `</CMD>`) after a combo "
                    + "before typing plain text, otherwise text is sent as keyboard shortcuts.\n"
                    + "Then use `terminal` steps for the commands (each is auto-suffixed with `<ENTER>`). "
                    + "Since no output is captured, avoid commands that depend on reading previous output.";
        } else {
            // SSH mode — full terminal access
            String user = activeProfile.getUsername() != null ? activeProfile.getUsername() : "";
            String host = activeProfile.getHost() != null ? activeProfile.getHost() : "";
            int port = activeProfile.getPort();

            return "**Execution mode: Terminal (SSH)**\n"
                    + "Target: " + user + "@" + host + ":" + port + "\n"
                    + "OS: " + osName + "\n"
                    + "Max steps: " + maxSteps + " (do NOT exceed this; use the fewest steps possible)\n\n"
                    + "Terminal commands are executed via SSH on the remote device. "
                    + "Output (stdout + stderr) is captured and will be summarized for the user. "
                    + "Use `terminal` steps freely — they run directly.\n"
                    + "IMPORTANT: Use only the correct commands for " + osName + ". "
                    + "If a command fails, you will get a chance to retry with an alternative "
                    + "— do NOT pre-plan fallback commands.";
        }
    }

    /**
     * Build the MACRO_CONTEXT string listing available macros.
     * Returns a formatted section if macros exist, or a notice if none.
     */
    @NonNull
    private String buildMacroContext() {
        try {
            MacrosManager mm = MacrosManager.getInstance(context);
            List<MacrosManager.Macro> macros = mm.getAllMacros();
            if (macros == null || macros.isEmpty()) {
                return "_No macros are currently defined._";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Available macros\n")
              .append("| Label | Key count |\n")
              .append("|---|---|\n");

            for (MacrosManager.Macro macro : macros) {
                String label = macro.name != null ? macro.name : "(unnamed)";
                int keyCount = macro.getKeyCount();
                sb.append("| ").append(label)
                  .append(" | `").append(keyCount).append(" keys` |\n");
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to build macro context", e);
            return "_No macros are currently defined._";
        }
    }

    // ── Target OS ────────────────────────────────────────────────────────

    /** Read the target OS: from active profile if available, else from prefs, else default. */
    @NonNull
    private String getTargetOs() {
        if (activeProfile != null && activeProfile.getTargetOs() != null
                && !activeProfile.getTargetOs().isEmpty()) {
            return activeProfile.getTargetOs();
        }
        SharedPreferences prefs = context.getSharedPreferences(AGENT_PREFS_NAME, Context.MODE_PRIVATE);
        String os = prefs.getString(PREF_AGENT_TARGET_OS, "linux");
        return os != null && !os.isEmpty() ? os : "linux";
    }

    @NonNull
    private String osDisplayName(@NonNull String os) {
        switch (os) {
            case "windows": return "Windows";
            case "macos":   return "macOS";
            default:        return "Linux";
        }
    }

    // ── Prompt file loading ──────────────────────────────────────────────

    /**
     * Load a prompt template with priority:
     * 1. User-edited prompt from SharedPreferences
     * 2. Bundled .md file from assets/Prompts/
     * 3. Hardcoded fallback
     */
    @NonNull
    private String loadPrompt(@NonNull String roleId, @NonNull String fallback) {
        // 1. Check for user-edited override
        SharedPreferences prefs = context.getSharedPreferences(AGENT_PREFS_NAME, Context.MODE_PRIVATE);
        String userPrompt = prefs.getString(roleId + "Prompt", "");
        if (userPrompt != null && !userPrompt.isEmpty()) {
            Log.d(TAG, "Using user-edited prompt for role: " + roleId);
            return userPrompt;
        }

        // 2. Try to load from assets
        String assetPath = "Prompts/" + roleId + ".md";
        String fromAssets = loadFromAssets(assetPath);
        if (fromAssets != null) {
            Log.d(TAG, "Loaded prompt from assets: " + assetPath);
            return fromAssets;
        }

        // 3. Fall back to hardcoded string
        Log.d(TAG, "Using hardcoded fallback prompt for role: " + roleId);
        return fallback;
    }

    /**
     * Load a text file from assets. Returns null if the file doesn't exist or can't be read.
     */
    @Nullable
    private String loadFromAssets(@NonNull String path) {
        AssetManager assets = context.getAssets();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assets.open(path), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            Log.w(TAG, "Failed to load asset: " + path, e);
            return null;
        }
    }
}
