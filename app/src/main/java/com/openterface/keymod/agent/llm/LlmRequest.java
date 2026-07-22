package com.openterface.keymod.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM API request model with fluent builder and JSON serialization.
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

    public LlmRequest addSystemMessage(String content) {
        messages.add(new Message("system", content));
        return this;
    }

    public LlmRequest addUserMessage(String content) {
        messages.add(new Message("user", content));
        return this;
    }

    public LlmRequest addAssistantMessage(String content) {
        messages.add(new Message("assistant", content));
        return this;
    }

    /** Serialize to JSON body for OpenAI-compatible /chat/completions */
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
            msgs.put(msgObj);
        }
        body.put("messages", msgs);
        return body;
    }

    public static final class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
