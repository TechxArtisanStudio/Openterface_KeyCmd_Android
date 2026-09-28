package com.openterface.keymod.agent.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.openterface.keymod.agent.ui.AgentMessage;
import com.openterface.keymod.agent.ui.AgentPlanStep;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages Agent conversation messages with optional persistence.
 *
 * <p>Stores messages in memory and can serialize to/from SharedPreferences
 * for session restoration across app restarts.</p>
 */
public final class AgentSession {

    private static final String PREFS_NAME = "agent_session";
    private static final String KEY_MESSAGES = "messages_json";

    private final List<AgentMessage> messages = new ArrayList<>();
    private final SharedPreferences prefs;

    public AgentSession(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Message management ───────────────────────────────────────────────

    /** Add a user message */
    public void addUserMessage(@NonNull String text) {
        messages.add(AgentMessage.user(text));
    }

    /** Add an assistant message */
    public void addAssistantMessage(@NonNull String text) {
        messages.add(AgentMessage.assistant(text));
    }

    /** Add a plan message with steps */
    public void addPlanMessage(@NonNull List<AgentPlanStep> steps) {
        messages.add(AgentMessage.plan(steps));
    }

    /** Add an act bar message (approve/cancel controls) */
    public void addActBarMessage() {
        messages.add(AgentMessage.actBar());
    }

    /** Add a terminal execution result */
    public void addExecutionCliMessage(@NonNull List<String> lines) {
        messages.add(AgentMessage.executionCli(lines));
    }

    /** Add a macro execution progress message */
    public void addExecutionMacroMessage(@NonNull List<String> steps,
                                          int progress, int currentStep,
                                          String statusChip) {
        messages.add(AgentMessage.executionMacro(steps, progress, currentStep, statusChip));
    }

    /** Add a raw AgentMessage */
    public void addMessage(@NonNull AgentMessage message) {
        messages.add(message);
    }

    /** Get an unmodifiable view of all messages */
    @NonNull
    public List<AgentMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /** Number of messages */
    public int size() {
        return messages.size();
    }

    /** Clear all messages and persist */
    public void clear() {
        messages.clear();
        save();
    }

    // ── Persistence ──────────────────────────────────────────────────────

    /** Save current messages to SharedPreferences */
    public void save() {
        try {
            JSONArray array = new JSONArray();
            for (AgentMessage msg : messages) {
                array.put(serializeMessage(msg));
            }
            prefs.edit().putString(KEY_MESSAGES, array.toString()).apply();
        } catch (JSONException e) {
            // Serialization should not fail with our simple model
            android.util.Log.e("AgentSession", "Failed to save session", e);
        }
    }

    /** Load messages from SharedPreferences */
    public void load() {
        String json = prefs.getString(KEY_MESSAGES, null);
        if (json == null) return;

        try {
            JSONArray array = new JSONArray(json);
            messages.clear();
            for (int i = 0; i < array.length(); i++) {
                messages.add(deserializeMessage(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            android.util.Log.e("AgentSession", "Failed to load session", e);
        }
    }

    // ── Serialization helpers ────────────────────────────────────────────

    @NonNull
    private static JSONObject serializeMessage(@NonNull AgentMessage msg) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", msg.type.name());
        obj.put("text", msg.text != null ? msg.text.toString() : null);

        // PLAN: persist planSteps + isHidMode
        if (!msg.planSteps.isEmpty()) {
            JSONArray stepsArray = new JSONArray();
            for (AgentPlanStep step : msg.planSteps) {
                stepsArray.put(step.toJSONObject());
            }
            obj.put("planSteps", stepsArray);
        }
        obj.put("isHidMode", msg.isHidMode);

        // EXECUTION_CLI: persist terminalLines + completion state
        if (!msg.terminalLines.isEmpty()) {
            JSONArray linesArray = new JSONArray();
            for (String line : msg.terminalLines) {
                linesArray.put(line);
            }
            obj.put("terminalLines", linesArray);
        }
        obj.put("isComplete", msg.isComplete);
        obj.put("isError", msg.isError);
        obj.put("canRetry", msg.canRetry);
        obj.put("cliSuccess", msg.cliSuccess != null ? msg.cliSuccess.booleanValue() : JSONObject.NULL);
        obj.put("cliRetried", msg.cliRetried);

        // EXECUTION_MACRO: persist macro steps + progress
        if (!msg.macroSteps.isEmpty()) {
            JSONArray macroArray = new JSONArray();
            for (String step : msg.macroSteps) {
                macroArray.put(step);
            }
            obj.put("macroSteps", macroArray);
        }
        obj.put("macroProgress", msg.macroProgress);
        obj.put("macroCurrentStep", msg.macroCurrentStep);
        obj.put("macroStatusChip", msg.macroStatusChip);

        // Note: CharSequence styling (e.g. ImageSpan) is lost on serialization.
        return obj;
    }

    @NonNull
    private static AgentMessage deserializeMessage(@NonNull JSONObject obj) throws JSONException {
        String typeName = obj.getString("type");
        String text = obj.optString("text", "");

        AgentMessage.Type type;
        try {
            type = AgentMessage.Type.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = AgentMessage.Type.ASSISTANT;
        }

        // ACT_BAR and THINKING are transient — restore as ASSISTANT
        if (type == AgentMessage.Type.ACT_BAR || type == AgentMessage.Type.THINKING) {
            return AgentMessage.assistant(text);
        }

        switch (type) {
            case USER:
                return AgentMessage.user(text);

            case ASSISTANT: {
                boolean isError = obj.optBoolean("isError", false);
                boolean canRetry = obj.optBoolean("canRetry", false);
                if (isError) {
                    return AgentMessage.assistantError(text != null ? text : "", canRetry);
                }
                return AgentMessage.assistant(text);
            }

            case PLAN: {
                List<AgentPlanStep> steps = new ArrayList<>();
                JSONArray stepsArray = obj.optJSONArray("planSteps");
                if (stepsArray != null) {
                    for (int i = 0; i < stepsArray.length(); i++) {
                        steps.add(AgentPlanStep.fromJSONObject(stepsArray.getJSONObject(i)));
                    }
                }
                boolean isHidMode = obj.optBoolean("isHidMode", false);
                return AgentMessage.plan(steps, isHidMode);
            }

            case EXECUTION_CLI: {
                List<String> lines = new ArrayList<>();
                JSONArray linesArray = obj.optJSONArray("terminalLines");
                if (linesArray != null) {
                    for (int i = 0; i < linesArray.length(); i++) {
                        lines.add(linesArray.getString(i));
                    }
                }
                boolean isComplete = obj.optBoolean("isComplete", false);
                boolean cliRetried = obj.optBoolean("cliRetried", false);
                if (cliRetried) {
                    return AgentMessage.executionCliRetried(lines);
                }
                if (isComplete) {
                    Boolean cliSuccess = null;
                    if (!obj.isNull("cliSuccess")) {
                        cliSuccess = obj.optBoolean("cliSuccess", true);
                    }
                    return AgentMessage.executionCliComplete(lines, cliSuccess != null && cliSuccess);
                }
                return AgentMessage.executionCli(lines);
            }

            case EXECUTION_MACRO: {
                List<String> macroSteps = new ArrayList<>();
                JSONArray macroArray = obj.optJSONArray("macroSteps");
                if (macroArray != null) {
                    for (int i = 0; i < macroArray.length(); i++) {
                        macroSteps.add(macroArray.getString(i));
                    }
                }
                int progress = obj.optInt("macroProgress", 0);
                int currentStep = obj.optInt("macroCurrentStep", 0);
                String statusChip = obj.optString("macroStatusChip", null);
                return AgentMessage.executionMacro(macroSteps, progress, currentStep, statusChip);
            }

            default:
                return AgentMessage.assistant(text);
        }
    }
}
