package com.openterface.keymod.agent.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for mapping provider display names to ProviderAdapter implementations.
 *
 * Usage:
 * <pre>
 *   ProviderAdapter adapter = ProviderAdapterFactory.get("OpenAI");
 *   // or
 *   ProviderAdapter adapter = ProviderAdapterFactory.get("Anthropic"); // returns stub
 * </pre>
 */
public final class ProviderAdapterFactory {

    private static final Map<String, ProviderAdapter> ADAPTERS = new LinkedHashMap<>();

    static {
        // OpenAI-compatible providers share the same adapter
        OpenAIAdapter openAI = new OpenAIAdapter();
        ADAPTERS.put("OpenAI", openAI);
        ADAPTERS.put("Mistral", openAI);
        ADAPTERS.put("Groq", openAI);
        ADAPTERS.put("DashScope", openAI);
        ADAPTERS.put("DeepSeek", openAI);
        ADAPTERS.put("Custom", openAI);

        // Stub adapters — to be fully implemented in Day 4
        ADAPTERS.put("Anthropic", new AnthropicAdapter());
        ADAPTERS.put("Google", new GoogleAdapter());
    }

    /**
     * Get the adapter for a given provider display name.
     *
     * @param providerName display name from the UI spinner
     *                     (e.g. "OpenAI", "Anthropic", "Custom")
     * @return the corresponding adapter
     * @throws IllegalArgumentException if provider name is not recognized
     */
    public static ProviderAdapter get(String providerName) {
        ProviderAdapter adapter = ADAPTERS.get(providerName);
        if (adapter == null) {
            // Default to OpenAI-compatible for unknown providers
            return new OpenAIAdapter();
        }
        return adapter;
    }

    /** Check if a provider is fully implemented (not a stub) */
    public static boolean isSupported(String providerName) {
        ProviderAdapter adapter = ADAPTERS.get(providerName);
        return adapter != null && adapter.unsupportedMessage() == null;
    }
}
