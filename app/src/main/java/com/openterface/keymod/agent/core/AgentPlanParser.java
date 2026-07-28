package com.openterface.keymod.agent.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.openterface.keymod.agent.llm.LlmResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM JSON responses into structured {@link AgentPlan} objects.
 *
 * <p>Implements a 5-level degradation strategy for extracting JSON from LLM output:
 * <ol>
 *   <li>Pre-pass: strip {@code <think>...<\/think>} reasoning blocks</li>
 *   <li>Level 1: extract from {@code ```json ... ```} fenced block</li>
 *   <li>Level 2: extract from any {@code ``` ... ```} fenced block</li>
 *   <li>Level 3: parse entire cleaned string as JSON</li>
 *   <li>Level 4: extract text between first {@code {} and last {@code }}</li>
 *   <li>Level 5: throw PlanParseException</li>
 * </ol>
 *
 * <p>Accepted JSON field names:
 * <ul>
 *   <li>Summary: {@code summary} or {@code intro}</li>
 *   <li>Steps array: {@code steps}, {@code plan}, or {@code actions}</li>
 *   <li>Command: {@code command}, {@code payload}, {@code shell}, or {@code cmd}</li>
 *   <li>Macro ID: {@code macroId}, {@code macro_id}, or {@code macro}</li>
 * </ul>
 */
public final class AgentPlanParser {

    private final Gson gson = new Gson();

    /** Pre-compiled regex for <think>...</think> blocks (reasoning models). */
    private static final Pattern THINK_PATTERN =
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    /** Pre-compiled regex for ```json ... ``` fences. */
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("```json\\s*\\n(.*?)\\n```", Pattern.DOTALL);

    /** Pre-compiled regex for any ``` ... ``` fences. */
    private static final Pattern ANY_FENCE_PATTERN =
            Pattern.compile("```\\s*\\n(.*?)\\n```", Pattern.DOTALL);

    /**
     * Parse an LLM JSON response string into an AgentPlan.
     *
     * @param jsonResponse the full response text from the LLM
     * @return parsed plan
     * @throws PlanParseException if JSON is malformed or missing required fields
     */
    @NonNull
    public AgentPlan parse(@NonNull String jsonResponse) throws PlanParseException {
        // Pre-pass: strip <think>...</think> reasoning blocks (DeepSeek R1, etc.)
        String cleaned = stripThinkingBlocks(jsonResponse);

        // Level 1: try ```json ... ``` fence
        String extracted = extractFence(cleaned, JSON_FENCE_PATTERN);
        if (extracted != null) {
            AgentPlan plan = tryParseJson(extracted);
            if (plan != null) return plan;
        }

        // Level 2: try any ``` ... ``` fence
        extracted = extractFence(cleaned, ANY_FENCE_PATTERN);
        if (extracted != null) {
            AgentPlan plan = tryParseJson(extracted);
            if (plan != null) return plan;
        }

        // Level 3: try parsing the entire cleaned string
        AgentPlan plan = tryParseJson(cleaned);
        if (plan != null) return plan;

        // Level 4: extract between first { and last }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            String slice = cleaned.substring(firstBrace, lastBrace + 1);
            plan = tryParseJson(slice);
            if (plan != null) return plan;
        }

        // Level 5: complete failure
        throw new PlanParseException(
                "Failed to parse plan from LLM response after 5 fallback attempts. "
                + "Raw response length: " + jsonResponse.length() + " chars.");
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

    // ── Internal parsing helpers ─────────────────────────────────────────

    /**
     * Strip <think>...</think> reasoning blocks from the response.
     * Some reasoning models (DeepSeek R1, etc.) wrap their chain-of-thought
     * in these tags before the actual JSON response.
     */
    @NonNull
    private String stripThinkingBlocks(@NonNull String text) {
        Matcher matcher = THINK_PATTERN.matcher(text);
        return matcher.replaceAll("").trim();
    }

    /**
     * Extract content matching a fence pattern.
     * Returns null if no match found.
     */
    @Nullable
    private String extractFence(@NonNull String text, @NonNull Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Attempt to parse a JSON string into an AgentPlan.
     * Returns null on any parsing failure (Gson is lenient by default).
     */
    @Nullable
    private AgentPlan tryParseJson(@NonNull String json) {
        try {
            PlanJson root = gson.fromJson(json, PlanJson.class);

            if (root == null) {
                return null;
            }

            // Summary/intro is optional — auto-generate if missing
            String summary = (root.summary != null && !root.summary.isEmpty())
                    ? root.summary
                    : "Plan with " + (root.steps != null ? root.steps.size() : 0) + " steps";

            if (root.steps == null || root.steps.isEmpty()) {
                // LLM returned valid JSON but no actionable steps
                return null;
            }

            List<AgentPlan.Step> steps = new ArrayList<>(root.steps.size());
            for (int i = 0; i < root.steps.size(); i++) {
                steps.add(parseStep(root.steps.get(i), i));
            }

            return new AgentPlan(summary, steps);
        } catch (JsonSyntaxException | IllegalArgumentException | PlanParseException e) {
            return null;
        }
    }

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
     * wrap around JSON output. Used only as a simple pre-cleanup — the main
     * parser uses the 5-level fallback strategy instead.
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
        // "summary" is standard; "intro" is used by the iOS spec
        @SerializedName(value = "summary", alternate = {"intro"})
        String summary;
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
