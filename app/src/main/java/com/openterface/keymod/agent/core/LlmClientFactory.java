package com.openterface.keymod.agent.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openterface.keymod.agent.llm.LlmHttpClient;
import com.openterface.keymod.agent.settings.AIConfigProvider;

/**
 * Shared factory for {@link LlmHttpClient} instances.
 *
 * <p>Centralizes the AI config lookup and timeout constants so that
 * PlanGenerationUseCase, PlanExecutionUseCase, and SummaryUseCase all
 * create clients with identical settings.</p>
 */
final class LlmClientFactory {

    /** Read timeout for Agent LLM calls (60s — plan generation can be slow). */
    static final int AGENT_READ_TIMEOUT_MS = 60_000;

    private final Context appContext;

    LlmClientFactory(@NonNull Context appContext) {
        this.appContext = appContext;
    }

    /**
     * Create a new LlmHttpClient with Agent-appropriate timeouts.
     * Reads API key, endpoint, and provider from AIConfigProvider.
     */
    @NonNull
    LlmHttpClient create() {
        AIConfigProvider config = AIConfigProvider.getInstance(appContext);
        return new LlmHttpClient(
                config.getApiKey(),
                config.getEndpoint(),
                config.getAdapter(),
                LlmHttpClient.DEFAULT_CONNECT_TIMEOUT_MS,
                AGENT_READ_TIMEOUT_MS);
    }

    /** Convenience: get the current AIConfigProvider. */
    @NonNull
    AIConfigProvider getConfig() {
        return AIConfigProvider.getInstance(appContext);
    }
}
