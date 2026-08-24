package com.openterface.keymod.agent.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for mapping provider display names to ProviderAdapter implementations.
 *
 * Usage:
 * <pre>
 *   ProviderAdapter adapter = ProviderAdapterFactory.get("OpenAI");
 *   ProviderAdapter adapter = ProviderAdapterFactory.get("Anthropic");
 *   ProviderAdapter adapter = ProviderAdapterFactory.get("Google");
 * </pre>
 */
public final class ProviderAdapterFactory {

    /**
     * Canonical provider adapter names (English, locale-independent).
     * These map to registered adapters and are used for SharedPreferences index → adapter lookup.
     * <p>
     * MUST match the order of {@code R.array.settings_ai_provider_names} in strings.xml:
     * <pre>
     *   0: OpenAI    1: Anthropic    2: Google    3: Mistral
     *   4: Groq      5: DashScope   6: DeepSeek   7: Custom
     * </pre>
     */
    public static final String[] ADAPTER_NAMES = {
            "OpenAI", "Anthropic", "Google", "Mistral", "Groq", "DashScope", "DeepSeek", "Custom"
    };

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

        // Provider-specific adapters
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
            throw new IllegalArgumentException(
                    "Unknown provider: '" + providerName + "'. "
                    + "Supported providers: " + ADAPTERS.keySet());
        }
        return adapter;
    }

    /** Check if a provider is fully implemented (not a stub) */
    public static boolean isSupported(String providerName) {
        ProviderAdapter adapter = ADAPTERS.get(providerName);
        return adapter != null && adapter.unsupportedMessage() == null;
    }
}
