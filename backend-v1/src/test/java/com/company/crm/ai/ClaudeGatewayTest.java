package com.company.crm.ai;

import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.ClaudeGateway;
import com.company.crm.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** API-G11: without ANTHROPIC_API_KEY the model-backed features answer 503 and nothing is sent. */
class ClaudeGatewayTest {

    @Test
    void withoutAKeyTheGatewayRefusesWithoutCallingOut() {
        ClaudeGateway gateway = new ClaudeGateway("", "claude-opus-5-5");
        assertThat(gateway.isConfigured()).isFalse();
        assertThatThrownBy(() -> gateway.structured("system", "prompt", SentimentJudgement.class))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(new ClaudeGateway("sk-ant-test", "claude-opus-5-5").isConfigured()).isTrue();
    }
}
