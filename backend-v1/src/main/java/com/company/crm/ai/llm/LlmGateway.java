package com.company.crm.ai.llm;

import java.util.Optional;

/**
 * API-G11: the language model behind the AI features that need one (chat, writing, sentiment).
 * Lead scoring and forecasting are computed from CRM data and don't use it.
 */
public interface LlmGateway {

    /** False when no model provider is configured — the AI endpoints that need one then answer 503. */
    boolean isConfigured();

    /**
     * Asks the model and returns its answer as {@code type} (JSON constrained to that shape), or
     * empty if the model declined to answer.
     */
    <T> Optional<T> structured(String system, String prompt, Class<T> type);
}
