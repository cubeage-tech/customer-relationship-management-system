package com.company.crm.ai.llm;

import java.util.Optional;

/**
 * API-G11: one language-model provider (Anthropic Claude, xAI Grok). The rest of the CRM talks to
 * {@link LlmGateway}, which uses whichever provider is configured (AI_PROVIDER).
 */
public interface LlmProvider {

    /** Short id, as in AI_PROVIDER and the integration log: "anthropic", "xai". */
    String key();

    /** True when its API key is set. */
    boolean isConfigured();

    /** The model's answer as {@code type}, or empty if the model declined. */
    <T> Optional<T> structured(String system, String prompt, Class<T> type);
}
