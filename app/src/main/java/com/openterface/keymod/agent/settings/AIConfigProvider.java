package com.openterface.keymod.agent.settings;

import android.content.Context;

import com.openterface.keymod.agent.llm.ProviderAdapter;
import com.openterface.keymod.agent.llm.ProviderAdapterFactory;

/**
 * Unified facade for AI configuration access.
 * <p>
 * This is the single source of truth for all AI-related configuration.
 * All consumers (AgentController, AgentFragment, VoiceInputFragment, etc.)
 * should use this class instead of directly accessing SharedPreferences.
 * </p>
 * <p>
 * Architecture:
 * <pre>
 * UI Layer (Fragments)
 *         ↓
 * AIConfigProvider (this class)
 *         ↓
 * AIProviderManager + AIKeyManager (data layer)
 * </pre>
 * </p>
 */
public class AIConfigProvider {

    private static volatile AIConfigProvider instance;

    private final AIProviderManager providerManager;
    private final AIKeyManager keyManager;
    private final Context context;

    private AIConfigProvider(Context context) {
        this.context = context.getApplicationContext();
        this.providerManager = AIProviderManager.getInstance(this.context);
        this.keyManager = AIKeyManager.getInstance(this.context);
    }

    /**
     * Get the singleton instance of AIConfigProvider.
     *
     * @param context Android context
     * @return AIConfigProvider instance
     */
    public static AIConfigProvider getInstance(Context context) {
        if (instance == null) {
            synchronized (AIConfigProvider.class) {
                if (instance == null) {
                    instance = new AIConfigProvider(context);
                }
            }
        }
        return instance;
    }

    /**
     * Get the currently selected provider.
     *
     * @return Selected AIProvider, or null if none selected
     */
    public AIProvider getSelectedProvider() {
        return providerManager.getSelectedProvider();
    }

    /**
     * Get the API endpoint URL for the currently selected provider.
     *
     * @return Endpoint URL, or empty string if not configured
     */
    public String getEndpoint() {
        AIProvider provider = getSelectedProvider();
        return provider != null ? provider.apiBaseURL : "";
    }

    /**
     * Get the model name for the currently selected provider.
     *
     * @return Model name, or empty string if not configured
     */
    public String getModel() {
        AIProvider provider = getSelectedProvider();
        return provider != null ? provider.modelName : "";
    }

    /**
     * Get the API key for the currently selected provider.
     *
     * @return API key, or empty string if not configured
     */
    public String getApiKey() {
        AIProvider provider = getSelectedProvider();
        if (provider == null) {
            return "";
        }
        String key = keyManager.getKey(provider.id);
        return key != null ? key : "";
    }

    /**
     * Get the provider name for adapter lookup.
     * <p>
     * This returns the canonical name used by ProviderAdapterFactory.
     * For custom providers, this returns "Custom".
     * </p>
     *
     * @return Provider name, or "OpenAI" as default
     */
    public String getProviderName() {
        AIProvider provider = getSelectedProvider();
        if (provider == null) {
            return "OpenAI";
        }

        // Map provider to adapter name
        // This should match the logic in ProviderAdapterFactory
        String name = provider.name;
        if (name == null) {
            return "OpenAI";
        }

        // Check if it's a known provider
        switch (name) {
            case "OpenAI":
            case "Anthropic":
            case "Google":
            case "Mistral":
            case "Groq":
            case "DashScope":
            case "DeepSeek":
                return name;
            case "Ollama (Local)":
            case "Local Qwen 0.6B":
            case "Local Qwen 1.7B":
                return "Custom"; // Local providers use Custom adapter
            default:
                // Custom user-defined provider
                return "Custom";
        }
    }

    /**
     * Check if the AI configuration is complete and ready to use.
     * <p>
     * This checks:
     * - A provider is selected
     * - Endpoint is configured
     * - API key is configured (if required by the provider)
     * </p>
     *
     * @return true if configuration is complete
     */
    public boolean isConfigured() {
        AIProvider provider = getSelectedProvider();
        if (provider == null) {
            return false;
        }

        // Check endpoint
        if (provider.apiBaseURL == null || provider.apiBaseURL.isEmpty()) {
            return false;
        }

        // Check API key (if required)
        if (!provider.apiKeyOptional) {
            String key = keyManager.getKey(provider.id);
            if (key == null || key.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the appropriate ProviderAdapter for the currently selected provider.
     *
     * @return ProviderAdapter instance
     */
    public ProviderAdapter getAdapter() {
        String providerName = getProviderName();
        return ProviderAdapterFactory.get(providerName);
    }

    /**
     * Determine the adapter name based on the endpoint URL.
     * <p>
     * This is useful for test connection functionality where we need to
     * determine the correct adapter before the provider is fully configured.
     * </p>
     *
     * @param url The API endpoint URL
     * @return Adapter name: "Anthropic", "Google", or "OpenAI"
     */
    public String getAdapterNameByURL(String url) {
        if (url == null || url.isEmpty()) {
            return "OpenAI";
        }

        String lowerUrl = url.toLowerCase();

        // Check for known provider URLs
        if (lowerUrl.contains("anthropic.com")) {
            return "Anthropic";
        }
        if (lowerUrl.contains("googleapis.com") || lowerUrl.contains("generativelanguage")) {
            return "Google";
        }

        // Default to OpenAI-compatible
        return "OpenAI";
    }

    /**
     * Get the underlying AIProviderManager instance.
     * <p>
     * This is provided for advanced use cases where direct access to the
     * provider list is needed (e.g., UI components that display all providers).
     * </p>
     *
     * @return AIProviderManager instance
     */
    public AIProviderManager getProviderManager() {
        return providerManager;
    }

    /**
     * Get the underlying AIKeyManager instance.
     * <p>
     * This is provided for advanced use cases where direct key management
     * is needed (e.g., UI components that manage API keys).
     * </p>
     *
     * @return AIKeyManager instance
     */
    public AIKeyManager getKeyManager() {
        return keyManager;
    }
}
