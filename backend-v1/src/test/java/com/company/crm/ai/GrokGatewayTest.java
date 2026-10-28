package com.company.crm.ai;

import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.GrokGateway;
import com.company.crm.ai.llm.JsonSchemas;
import com.company.crm.ai.llm.LlmProvider;
import com.company.crm.ai.llm.LlmRouter;
import com.company.crm.ai.service.AiQuotationDraftService.DraftPlan;
import com.company.crm.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** API-G11: xAI Grok as the language model — the request it sends, and how it reads answers and failures. */
class GrokGatewayTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private volatile long delayMs;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GrokGateway gateway(long timeoutSeconds) {
        return new GrokGateway(mapper, "xai-test-key", "grok-4.20-0309-non-reasoning",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", 4096, timeoutSeconds);
    }

    private String answer(String content) throws Exception {
        return mapper.writeValueAsString(mapper.createObjectNode().set("choices", mapper.createArrayNode().add(
                mapper.createObjectNode().put("finish_reason", "stop").set("message",
                        mapper.createObjectNode().put("role", "assistant").put("content", content)))));
    }

    @Test
    void asksForAStrictJsonSchemaAnswerAndReadsItIntoTheRecord() throws Exception {
        responseBody.set(answer("{\"sentiment\":\"negative\",\"score\":-0.6,\"churnRisk\":\"high\",\"reason\":\"Threatens to cancel\"}"));

        Optional<SentimentJudgement> result = gateway(8).structured("Judge the sentiment.",
                "Mail from buyer@acme.test, phone +919876543210: we will cancel", SentimentJudgement.class);

        assertThat(result).contains(new SentimentJudgement("negative", -0.6, "high", "Threatens to cancel"));
        assertThat(auth.get()).isEqualTo("Bearer xai-test-key");
        JsonNode sent = mapper.readTree(requestBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("grok-4.20-0309-non-reasoning");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(4096);
        assertThat(sent.path("messages").get(0).path("role").asText()).isEqualTo("system");
        String user = sent.path("messages").get(1).path("content").asText();
        assertThat(user).doesNotContain("buyer@acme.test").doesNotContain("9876543210"); // SEC-5
        JsonNode format = sent.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        JsonNode schema = format.path("json_schema").path("schema");
        assertThat(schema.path("properties").has("churnRisk")).isTrue();
        assertThat(schema.path("required")).hasSize(4);
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void aRefusalIsEmptyAndFailuresAreClear() throws Exception {
        responseBody.set(mapper.writeValueAsString(mapper.createObjectNode().set("choices", mapper.createArrayNode().add(
                mapper.createObjectNode().set("message", mapper.createObjectNode().put("refusal", "I can't help with that"))))));
        assertThat(gateway(8).structured("s", "p", SentimentJudgement.class)).isEmpty();

        status.set(401);
        responseBody.set("{\"error\":\"Incorrect API key provided\"}");
        assertThatThrownBy(() -> gateway(8).structured("s", "p", SentimentJudgement.class))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("AI_UNAVAILABLE");

        status.set(429);
        assertThatThrownBy(() -> gateway(8).structured("s", "p", SentimentJudgement.class))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("AI_RATE_LIMITED");

        // NFR-7.1c: no waiting past the budget.
        status.set(200);
        responseBody.set(answer("{}"));
        delayMs = 2500;
        assertThatThrownBy(() -> gateway(1).structured("s", "p", SentimentJudgement.class))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("AI_TIMEOUT");
    }

    @Test
    void nestedRecordsGetStrictSchemasToo() {
        JsonNode schema = JsonSchemas.of(DraftPlan.class);
        assertThat(schema.path("required").toString()).contains("lines").contains("coverNote");
        JsonNode line = schema.path("properties").path("lines").path("items");
        JsonNode resolved = line.has("$ref") ? schema.path("$defs").path(line.path("$ref").asText().replace("#/$defs/", "")) : line;
        assertThat(resolved.path("properties").has("discountPercent")).isTrue();
        assertThat(resolved.path("required")).hasSize(4);
        assertThat(resolved.path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void theRouterUsesTheConfiguredProvider() {
        LlmProvider claude = provider("anthropic", false);
        LlmProvider grok = provider("xai", true);
        assertThat(new LlmRouter(List.of(claude, grok), "auto").provider()).isEqualTo("xai");      // only Grok has a key
        assertThat(new LlmRouter(List.of(provider("anthropic", true), grok), "auto").provider()).isEqualTo("anthropic");
        assertThat(new LlmRouter(List.of(provider("anthropic", true), grok), "xai").provider()).isEqualTo("xai");
        assertThat(new LlmRouter(List.of(claude, grok), "anthropic").isConfigured()).isFalse();   // chosen, but no key
    }

    private static LlmProvider provider(String key, boolean configured) {
        return new LlmProvider() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public <T> Optional<T> structured(String system, String prompt, Class<T> type) {
                return Optional.empty();
            }
        };
    }
}
