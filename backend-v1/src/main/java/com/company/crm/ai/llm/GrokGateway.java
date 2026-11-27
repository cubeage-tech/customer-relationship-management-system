package com.company.crm.ai.llm;

import com.company.crm.ai.privacy.AiRedactor;
import com.company.crm.common.exception.ApiException;
import com.company.crm.integration.calllog.IntegrationCallLog;
import com.company.crm.integration.health.IntegrationHealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

/**
 * API-G11: xAI Grok through its REST API (chat completions with a JSON-schema structured output), so
 * answers come back in the shape of a Java record, as with Claude. Key: XAI_API_KEY; model: XAI_MODEL.
 * The same rules as every provider: SEC-5 redaction of the prompt, INT-8 credential health, INT-9
 * call log, and the NFR-7.1c 8-second budget (one attempt, hard timeout).
 */
@Component
@Slf4j
public class GrokGateway implements LlmProvider {

    public static final String KEY = "xai";

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final long maxTokens;
    private final Duration timeout;
    private final HttpClient http;

    /** INT-8 (optional so the class can be built without Spring). */
    @Autowired(required = false)
    private IntegrationHealthService integrationHealth;

    /** INT-9 (optional, like the health tracker). */
    @Autowired(required = false)
    private IntegrationCallLog callLog;

    public GrokGateway(ObjectMapper objectMapper,
                       @Value("${app.ai.xai.api-key:}") String apiKey,
                       @Value("${app.ai.xai.model:grok-4.20-0309-non-reasoning}") String model,
                       @Value("${app.ai.xai.base-url:https://api.x.ai/v1}") String baseUrl,
                       @Value("${app.ai.max-tokens:4096}") long maxTokens,
                       @Value("${app.ai.timeout-seconds:8}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(5, timeoutSeconds))).build();
    }

    @Override
    public String key() {
        return KEY;
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
        String body;
        try {
            body = objectMapper.writeValueAsString(request(system, prompt, type));
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the AI request", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, KEY, "chat.completions");
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("Grok request timed out after {} s", timeout.toSeconds());
            call.failed(null, "timed out after " + timeout.toSeconds() + " s");
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The AI took too long to answer — try again", "AI_TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.failed(null, "interrupted");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not be reached — try again", "AI_UNAVAILABLE");
        } catch (Exception e) {
            log.warn("Grok request error: {}", e.getMessage());
            call.failed(null, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not be reached — try again", "AI_UNAVAILABLE");
        }

        int status = response.statusCode();
        if (status == 429) {
            call.failed(429, "rate limited");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy — try again in a moment", "AI_RATE_LIMITED");
        }
        if (status >= 300) {
            String error = errorMessage(response.body());
            log.warn("Grok request failed ({}): {}", status, error);
            call.failed(status, error);
            if ((status == 401 || status == 403) && integrationHealth != null) {
                integrationHealth.authFailed(IntegrationHealthService.XAI, "xAI answered HTTP " + status);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI service could not answer — try again", "AI_UNAVAILABLE");
        }
        call.ok(status);
        if (integrationHealth != null) {
            integrationHealth.ok(IntegrationHealthService.XAI);
        }
        return parse(response.body(), type);
    }

    /** The chat-completions request: system + redacted user message, a strict JSON-schema answer, a capped output. */
    ObjectNode request(String system, String prompt, Class<?> type) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        // SEC-5: contact, identity and account details never leave the CRM, whatever the caller built.
        messages.addObject().put("role", "user").put("content", AiRedactor.redact(prompt));
        ObjectNode format = root.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode schema = format.putObject("json_schema");
        schema.put("name", type.getSimpleName());
        schema.set("schema", JsonSchemas.of(type));
        schema.put("strict", true);
        return root;
    }

    private <T> Optional<T> parse(String body, Class<T> type) {
        try {
            JsonNode message = objectMapper.readTree(body).path("choices").path(0).path("message");
            JsonNode refusal = message.path("refusal");
            if (refusal.isTextual() && !refusal.asText().isBlank()) {
                log.info("AI request declined by the model");
                return Optional.empty();
            }
            String content = message.path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(content, type));
        } catch (Exception e) {
            log.warn("Grok answer could not be read: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI answer was incomplete — try again", "AI_UNAVAILABLE");
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode error = node.path("error");
            String text = error.isTextual() ? error.asText() : error.path("message").asText(node.path("message").asText(""));
            return text.isBlank() ? "HTTP error" : text;
        } catch (Exception e) {
            return body == null ? "HTTP error" : body.length() > 200 ? body.substring(0, 200) : body;
        }
    }
}
