package com.company.crm.ai.llm;

import com.company.crm.ai.privacy.AiRedactor;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.crm.integration.health.IntegrationHealthService;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.company.crm.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * API-G11: Claude through the official Anthropic Java SDK. Answers are structured outputs (JSON in
 * the shape of a Java record), so callers never parse free text. A refused request is retried on
 * a suitable model by the API's server-side fallback, and a refusal that remains comes back as
 * empty. Key: ANTHROPIC_API_KEY; model: AI_MODEL (default claude-opus-5-5).
 */
@Component
@Slf4j
public class ClaudeGateway implements LlmGateway {

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    /** INT-8: told when the provider rejects our credentials (optional so the class can be built without Spring). */
    @Autowired(required = false)
    private IntegrationHealthService integrationHealth;

    public ClaudeGateway(@Value("${app.ai.anthropic.api-key:}") String apiKey,
                         @Value("${app.ai.model:claude-opus-5-5}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public <T> Optional<T> structured(String system, String prompt, Class<T> type) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI is not configured for this CRM", "AI_NOT_CONFIGURED");
        }
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                // SEC-5: contact, identity and account details never leave the CRM, whatever the caller built.
                .addUserMessage(AiRedactor.redact(prompt))
                // Server-side fallback: a request the model declines is re-routed by refusal category.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(type)
                .build();
        try {
            StructuredMessage<T> response = client().messages().create(params);
            if (integrationHealth != null) {
                integrationHealth.ok(IntegrationHealthService.ANTHROPIC);
            }
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.info("AI request declined by the model");
                return Optional.empty();
            }
            return response.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
        } catch (RateLimitException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy — try again in a moment", "AI_RATE_LIMITED");
        } catch (AnthropicServiceException e) {
            log.warn("AI request failed ({}): {}", e.statusCode(), e.getMessage());
            if ((e.statusCode() == 401 || e.statusCode() == 403) && integrationHealth != null) {
                integrationHealth.authFailed(IntegrationHealthService.ANTHROPIC, "Anthropic answered HTTP " + e.statusCode());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not answer — try again", "AI_UNAVAILABLE");
        } catch (AnthropicException e) {
            log.warn("AI request error: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not be reached — try again", "AI_UNAVAILABLE");
        }
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
            }
        }
        return client;
    }
}
