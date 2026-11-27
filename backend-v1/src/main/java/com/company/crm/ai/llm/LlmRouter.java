package com.company.crm.ai.llm;

import com.company.crm.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * API-G11: the language model the CRM uses — AI_PROVIDER picks it (anthropic | xai). "auto" (the
 * default) uses Anthropic when ANTHROPIC_API_KEY is set, else xAI Grok when XAI_API_KEY is set.
 */
@Component
public class LlmRouter implements LlmGateway {

    private final List<LlmProvider> providers;
    private final String choice;

    public LlmRouter(List<LlmProvider> providers, @Value("${app.ai.provider:auto}") String choice) {
        this.providers = providers;
        this.choice = choice == null || choice.isBlank() ? "auto" : choice.trim().toLowerCase();
    }

    /** The provider in use, or empty when none is configured. */
    public Optional<LlmProvider> active() {
        if (!"auto".equals(choice)) {
            return providers.stream().filter(p -> p.key().equals(choice) || ("grok".equals(choice) && p.key().equals(GrokGateway.KEY)))
                    .filter(LlmProvider::isConfigured).findFirst();
        }
        return providers.stream().filter(p -> "anthropic".equals(p.key())).filter(LlmProvider::isConfigured).findFirst()
                .or(() -> providers.stream().filter(LlmProvider::isConfigured).findFirst());
    }

    @Override
    public boolean isConfigured() {
        return active().isPresent();
    }

    @Override
    public String provider() {
        return active().map(LlmProvider::key).orElse("none");
    }

    @Override
    public <T> Optional<T> structured(String system, String prompt, Class<T> type) {
        LlmProvider provider = active().orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI is not configured for this CRM", "AI_NOT_CONFIGURED"));
        return provider.structured(system, prompt, type);
    }
}
