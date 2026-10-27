package com.company.crm.ai.llm;

import com.company.crm.ai.privacy.AiRedactor;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.crm.integration.health.IntegrationHealthService;
import com.company.crm.integration.calllog.IntegrationCallLog;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.company.crm.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Optional;

/**
 * API-G11: Claude through the official Anthropic Java SDK. Answers are structured outputs (JSON in
 * the shape of a Java record), so callers never parse free text. A refused request is retried on
 * a suitable model by the API's server-side fallback, and a refusal that remains comes back as
 * empty. Key: ANTHROPIC_API_KEY; model: AI_MODEL (default claude-opus-5-5).
 *
 * <p>NFR-7.1c: generative answers must come within 8 seconds. Calls run at low effort with a small
 * output cap (AI_EFFORT, AI_MAX_TOKENS) and a hard 8-second timeout without automatic retries
 * (AI_TIMEOUT_SECONDS), so a slow answer fails fast with AI_TIMEOUT instead of keeping the user waiting.
 */
@Component
@Slf4j
public class ClaudeGateway implements LlmGateway {

    private final String apiKey;
    private final String model;
    private final String effort;
    private final long maxTokens;
    private final Duration timeout;
    private volatile AnthropicClient client;

    /** INT-8: told when the provider rejects our credentials (optional so the class can be built without Spring). */
    @Autowired(required = false)
    private IntegrationHealthService integrationHealth;

    /** INT-9: every call is logged (optional, like the health tracker). */
    @Autowired(required = false)
    private IntegrationCallLog callLog;

    public ClaudeGateway(@Value("${app.ai.anthropic.api-key:}") String apiKey,
                         @Value("${app.ai.model:claude-opus-5-5}") String model,
                         @Value("${app.ai.effort:low}") String effort,
                         @Value("${app.ai.max-tokens:4096}") long maxTokens,
                         @Value("${app.ai.timeout-seconds:8}") long timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.effort = effort;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
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
        StructuredMessageCreateParams<T> params = params(system, prompt, type);
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, "anthropic", "messages.create");
        try {
            StructuredMessage<T> response = client().messages().create(params);
            call.ok(200);
            if (integrationHealth != null) {
                integrationHealth.ok(IntegrationHealthService.ANTHROPIC);
            }
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.info("AI request declined by the model");
                return Optional.empty();
            }
            return response.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
        } catch (RateLimitException e) {
            call.failed(429, "rate limited");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy — try again in a moment", "AI_RATE_LIMITED");
        } catch (AnthropicServiceException e) {
            log.warn("AI request failed ({}): {}", e.statusCode(), e.getMessage());
            call.failed(e.statusCode(), e.getMessage());
            if ((e.statusCode() == 401 || e.statusCode() == 403) && integrationHealth != null) {
                integrationHealth.authFailed(IntegrationHealthService.ANTHROPIC, "Anthropic answered HTTP " + e.statusCode());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not answer — try again", "AI_UNAVAILABLE");
        } catch (AnthropicException e) {
            if (timedOut(e)) {
                log.warn("AI request timed out after {} s", timeout.toSeconds());
                call.failed(null, "timed out after " + timeout.toSeconds() + " s");
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The AI took too long to answer — try again", "AI_TIMEOUT");
            }
            log.warn("AI request error: {}", e.getMessage());
            call.failed(null, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not be reached — try again", "AI_UNAVAILABLE");
        }
    }

    /** The request: the caller's prompt (redacted), a structured answer of {@code type}, at the configured effort and cap. */
    <T> StructuredMessageCreateParams<T> params(String system, String prompt, Class<T> type) {
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                // SEC-5: contact, identity and account details never leave the CRM, whatever the caller built.
                .addUserMessage(AiRedactor.redact(prompt))
                // Server-side fallback: a request the model declines is re-routed by refusal category.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(type);
        // NFR-7.1c: the effort goes on the same output config as the JSON format the SDK derived from the record.
        OutputConfig withFormat = builder.build().rawParams().outputConfig().orElseThrow();
        return builder.outputConfig(withFormat.toBuilder().effort(OutputConfig.Effort.of(effort)).build()).build();
    }

    private static boolean timedOut(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    // NFR-7.1c: one attempt within the time budget (a retry would overrun it).
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).maxRetries(0).build();
                }
            }
        }
        return client;
    }
}
