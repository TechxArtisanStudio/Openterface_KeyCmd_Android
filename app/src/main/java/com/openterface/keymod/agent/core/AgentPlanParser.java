package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.openterface.keymod.agent.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses LLM JSON responses into structured {@link AgentPlan} objects.
 *
 * <p>The expected JSON format is:
 * <pre>{@code
 * {
 *   "summary": "one-line summary",
 *   "steps": [
 *     {"title": "step title", "command": "ls -la", "kind": "terminal"},
 *     {"title": "press save", "keys": "<CMD>s</CMD>", "kind": "hid"},
 *     {"title": "run cleanup", "macroId": "cleanup_1", "kind": "macro"}
 *   ]
 * }
 * }</pre>
 *
 * <p>Uses Gson for JSON parsing to avoid org.json stub issues in unit tests.</p>
 */
public final class AgentPlanParser {

    private final Gson gson = new Gson();

    /**
     * Parse an LLM JSON response string into an AgentPlan.
     *
     * @param jsonResponse the full response text from the LLM
     * @return parsed plan
     * @throws PlanParseException if JSON is malformed or missing required fields
     */
    @NonNull
    public AgentPlan parse(@NonNull String jsonResponse) throws PlanParseException {
        // Strip markdown code fences if present (LLMs sometimes wrap JSON in ```json...```)
        String cleaned = stripCodeFences(jsonResponse);

        try {
            PlanJson root = gson.fromJson(cleaned, PlanJson.class);

            if (root == null) {
                throw new PlanParseException("Failed to parse plan JSON: null result");
            }

            // Summary is optional — auto-generate if missing
            String summary = (root.summary != null && !root.summary.isEmpty())
                    ? root.summary
                    : "Plan with " + (root.steps != null ? root.steps.size() : 0) + " steps";

            if (root.steps == null || root.steps.isEmpty()) {
                // LLM returned valid JSON but no actionable steps — provide helpful error
                throw new PlanParseException(
                        "The AI model did not return a valid execution plan. "
                        + "Please try again or rephrase your request.");
            }

            List<AgentPlan.Step> steps = new ArrayList<>(root.steps.size());
            for (int i = 0; i < root.steps.size(); i++) {
                steps.add(parseStep(root.steps.get(i), i));
            }

            return new AgentPlan(summary, steps);

        } catch (JsonSyntaxException e) {
            throw new PlanParseException("Failed to parse plan JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parse from an {@link LlmResponse} (handles both streaming and non-streaming).
     *
     * @param response the LLM response
     * @return parsed plan
     * @throws PlanParseException if content is malformed
     */
    @NonNull
    public AgentPlan parseFromResponse(@NonNull LlmResponse response) throws PlanParseException {
        if (response.content == null || response.content.isEmpty()) {
            throw new PlanParseException("LLM response has no content");
        }
        return parse(response.content);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    @NonNull
    private AgentPlan.Step parseStep(@NonNull StepJson obj, int index) throws PlanParseException {
        String title = (obj.title != null && !obj.title.isEmpty())
                ? obj.title : "Step " + (index + 1);
        String kind = (obj.kind != null && !obj.kind.isEmpty()) ? obj.kind : "terminal";

        // Validate: each step must have its required payload
        switch (kind) {
            case "terminal":
                if (obj.command == null || obj.command.isEmpty()) {
                    throw new PlanParseException(
                            "Terminal step missing 'command' field at index " + index);
                }
                break;
            case "hid":
                if (obj.keys == null || obj.keys.isEmpty()) {
                    throw new PlanParseException(
                            "HID step missing 'keys' field at index " + index);
                }
                break;
            case "macro":
                if (obj.macroId == null || obj.macroId.isEmpty()) {
                    throw new PlanParseException(
                            "Macro step missing 'macroId' field at index " + index);
                }
                break;
            default:
                // Unknown kind — treat as terminal if command exists, else fail
                if (obj.command != null && !obj.command.isEmpty()) {
                    kind = "terminal";
                } else {
                    throw new PlanParseException(
                            "Unknown step kind '" + kind + "' at index " + index);
                }
        }

        return new AgentPlan.Step(index, title, obj.command, obj.keys, obj.macroId, kind);
    }

    /**
     * Strip markdown code fences ({@code ```json ... ```}) that LLMs sometimes
     * wrap around JSON output.
     */
    @NonNull
    static String stripCodeFences(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            return trimmed.trim();
        }
        return trimmed;
    }

    // ── Gson DTOs ────────────────────────────────────────────────────────

    /** Root plan JSON structure — supports multiple LLM output formats */
    private static final class PlanJson {
        @SerializedName("summary") String summary;
        // "steps" is standard; "plan" and "actions" are common LLM alternatives
        @SerializedName(value = "steps", alternate = {"plan", "actions"})
        List<StepJson> steps;
    }

    /** Single step JSON structure — supports multiple field naming conventions */
    private static final class StepJson {
        @SerializedName("title") String title;
        // "command" is standard; "payload" and "shell" are common LLM alternatives
        @SerializedName(value = "command", alternate = {"payload", "shell", "cmd"})
        String command;
        @SerializedName("keys") String keys;
        @SerializedName(value = "macroId", alternate = {"macro_id", "macro"})
        String macroId;
        @SerializedName("kind") String kind;
    }

    // ── Exception ────────────────────────────────────────────────────────

    /** Thrown when plan JSON is malformed or missing required fields */
    public static final class PlanParseException extends Exception {
        public PlanParseException(@NonNull String message) {
            super(message);
        }

        public PlanParseException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }
}
