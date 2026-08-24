package com.openterface.keymod.agent.settings;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

/**
 * AI service provider data model.
 * Stores and manages LLM service configuration.
 */
public class AIProvider {
    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("apiBaseURL")
    public String apiBaseURL;

    @SerializedName("modelName")
    public String modelName;

    @SerializedName("apiKeyOptional")
    public boolean apiKeyOptional;

    // No-arg constructor (required by Gson)
    public AIProvider() {}

    // Full constructor
    public AIProvider(String name, String apiBaseURL, String modelName, boolean apiKeyOptional) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.apiBaseURL = apiBaseURL;
        this.modelName = modelName;
        this.apiKeyOptional = apiKeyOptional;
    }

    // ========== Preset Provider Factory Methods ==========

    public static AIProvider createOpenAI() {
        return new AIProvider("OpenAI", "https://api.openai.com/v1", "gpt-4o", false);
    }

    public static AIProvider createAnthropic() {
        return new AIProvider("Anthropic", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022", false);
    }

    public static AIProvider createGoogle() {
        return new AIProvider("Google", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash", false);
    }

    public static AIProvider createMistral() {
        return new AIProvider("Mistral", "https://api.mistral.ai/v1", "mistral-large-latest", false);
    }

    public static AIProvider createGroq() {
        return new AIProvider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", false);
    }

    public static AIProvider createDashScope() {
        return new AIProvider("DashScope", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max", false);
    }

    public static AIProvider createDeepSeek() {
        return new AIProvider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", false);
    }

    public static AIProvider createOllamaLocal() {
        return new AIProvider("Ollama (Local)", "http://localhost:11434/v1", "llama3", true);
    }

    public static AIProvider createLocalQwen06B() {
        return new AIProvider("Local Qwen 0.6B", "local://qwen3-0.6b", "Qwen3-0.6B-4bit", true);
    }

    public static AIProvider createLocalQwen17B() {
        return new AIProvider("Local Qwen 1.7B", "local://qwen3-1.7b", "Qwen3-1.7B-4bit", true);
    }

    // ========== Utility Methods ==========

    /**
     * Returns the default list of preset providers.
     */
    public static java.util.List<AIProvider> getDefaultProviders() {
        java.util.List<AIProvider> list = new java.util.ArrayList<>();
        list.add(createOpenAI());
        list.add(createAnthropic());
        list.add(createGoogle());
        list.add(createMistral());
        list.add(createGroq());
        list.add(createDashScope());
        list.add(createDeepSeek());
        list.add(createOllamaLocal());
        list.add(createLocalQwen06B());
        list.add(createLocalQwen17B());
        return list;
    }

    /**
     * Checks whether this provider is a local model (URL starts with "local://").
     */
    public boolean isLocalModel() {
        return apiBaseURL != null && apiBaseURL.startsWith("local://");
    }

    /**
     * Checks whether this provider has valid configuration.
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               apiBaseURL != null && !apiBaseURL.trim().isEmpty() &&
               modelName != null && !modelName.trim().isEmpty();
    }

    /**
     * Creates a copy of this provider with a new ID (for creating new providers from presets).
     */
    public AIProvider copy() {
        AIProvider copy = new AIProvider();
        copy.id = UUID.randomUUID().toString();
        copy.name = this.name;
        copy.apiBaseURL = this.apiBaseURL;
        copy.modelName = this.modelName;
        copy.apiKeyOptional = this.apiKeyOptional;
        return copy;
    }

    @Override
    public String toString() {
        return "AIProvider{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", apiBaseURL='" + apiBaseURL + '\'' +
                ", modelName='" + modelName + '\'' +
                ", apiKeyOptional=" + apiKeyOptional +
                '}';
    }
}
