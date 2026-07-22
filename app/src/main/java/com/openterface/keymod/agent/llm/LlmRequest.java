package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM API request model with fluent builder and JSON serialization.
 *
 * Supports:
 *   - Standard text messages (system/user/assistant)
 *   - Tool calls (assistant → tool) and tool results (tool → assistant)
 *   - OpenAI-compatible /chat/completions body format
 */
public final class LlmRequest {
    public String model;
    public List<Message> messages;
    public int maxTokens;
    public boolean stream;
    public double temperature;

    public LlmRequest(String model) {
        this.model = model;
        this.messages = new ArrayList<>();
        this.maxTokens = 2048;
        this.stream = false;
        this.temperature = 0.7;
    }

    // ── Standard messages ────────────────────────────────────────────

    public LlmRequest addSystemMessage(String content) {
        messages.add(new Message("system", content));
        return this;
    }

    public LlmRequest addUserMessage(String content) {
        messages.add(new Message("user", content));
        return this;
    }

    public LlmRequest addAssistantMessage(String content) {
        messages.add(new Message("assistant", content, null, null, null));
        return this;
    }

    // ── Tool-related messages ────────────────────────────────────────

    /** Assistant message with tool calls (for returning tool invocations to the API) */
    public LlmRequest addAssistantToolCalls(String content, List<ToolCall> toolCalls) {
        messages.add(new Message("assistant", content, null, null, toolCalls));
        return this;
    }

    /** Tool result message (for sending tool execution results back) */
    public LlmRequest addToolResult(String toolCallId, String content) {
        messages.add(new Message("tool", content, null, toolCallId, null));
        return this;
    }

    // ── JSON serialization ───────────────────────────────────────────

    /** Serialize with this request's stream flag */
    public JSONObject toJson() throws org.json.JSONException {
        return toJson(this.stream);
    }

    /** Serialize with explicit stream flag (avoids mutating this.stream) */
    public JSONObject toJson(boolean streamOverride) throws org.json.JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", streamOverride);

        JSONArray msgs = new JSONArray();
        for (Message m : messages) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", m.role);
            msgObj.put("content", m.content);

            if (m.name != null) {
                msgObj.put("name", m.name);
            }
            if (m.toolCallId != null) {
                msgObj.put("tool_call_id", m.toolCallId);
            }
            if (m.toolCalls != null && !m.toolCalls.isEmpty()) {
                JSONArray toolCallsArr = new JSONArray();
                for (ToolCall tc : m.toolCalls) {
                    JSONObject tcObj = new JSONObject();
                    tcObj.put("id", tc.id);
                    tcObj.put("type", "function");
                    JSONObject fnObj = new JSONObject();
                    fnObj.put("name", tc.function.name);
                    fnObj.put("arguments", tc.function.arguments);
                    tcObj.put("function", fnObj);
                    toolCallsArr.put(tcObj);
                }
                msgObj.put("tool_calls", toolCallsArr);
            }

            msgs.put(msgObj);
        }
        body.put("messages", msgs);
        return body;
    }

    // ── Inner types ──────────────────────────────────────────────────

    /**
     * A single message in the conversation.
     * Roles: "system", "user", "assistant", "tool"
     */
    public static final class Message {
        public final String role;
        public final String content;
        public final String name;           // function name (tool messages)
        public final String toolCallId;     // tool call ID (tool messages)
        public final List<ToolCall> toolCalls;  // tool calls (assistant messages)

        /** Simple text message */
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }

        /** Full constructor for all message types */
        public Message(String role, String content, String name,
                       String toolCallId, List<ToolCall> toolCalls) {
            this.role = role;
            this.content = content;
            this.name = name;
            this.toolCallId = toolCallId;
            this.toolCalls = toolCalls;
        }
    }

    /** A single tool invocation returned by the model */
    public static final class ToolCall {
        public final String id;             // unique call ID (e.g. "call_abc123")
        public final String type;           // always "function"
        public final FunctionCall function;

        public ToolCall(String id, String type, FunctionCall function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }
    }

    /** The function name + arguments within a ToolCall */
    public static final class FunctionCall {
        public final String name;           // function name
        public final String arguments;      // JSON string of arguments

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }
}
