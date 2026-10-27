package com.company.crm.ai;

import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.ClaudeGateway;
import com.company.crm.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** API-G11: without ANTHROPIC_API_KEY the model-backed features answer 503 and nothing is sent. */
class ClaudeGatewayTest {

    @Test
    void withoutAKeyTheGatewayRefusesWithoutCallingOut() {
        ClaudeGateway gateway = new ClaudeGateway("", "claude-opus-5-5", "low", 4096, 8);
        assertThat(gateway.isConfigured()).isFalse();
        assertThatThrownBy(() -> gateway.structured("system", "prompt", SentimentJudgement.class))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(new ClaudeGateway("sk-ant-test", "claude-opus-5-5", "low", 4096, 8).isConfigured()).isTrue();
    }

    /** NFR-7.1c: low effort and a small output cap keep answers inside the 8-second budget; the JSON format stays. */
    @Test
    void requestsRunAtLowEffortWithACappedOutputAndKeepTheirFormat() {
        ClaudeGateway gateway = new ClaudeGateway("sk-ant-test", "claude-opus-5-5", "low", 4096, 8);
        StructuredMessageCreateParams<?> params = ReflectionTestUtils.invokeMethod(gateway, "params", "system",
                "Call +919876543210 about the order", SentimentJudgement.class);

        OutputConfig config = params.rawParams().outputConfig().orElseThrow();
        assertThat(config.effort()).contains(OutputConfig.Effort.LOW);
        assertThat(config.format()).isPresent();
        assertThat(params.rawParams().maxTokens()).isEqualTo(4096L);
        assertThat(params.rawParams().messages().get(0).content().toString()).doesNotContain("9876543210"); // SEC-5
    }
}
