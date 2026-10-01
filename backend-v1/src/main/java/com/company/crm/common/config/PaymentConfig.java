package com.company.crm.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Value("${payment.gateway.key-id:}")
    private String keyId;

    @Value("${payment.gateway.key-secret:}")
    private String keySecret;

    @Value("${payment.gateway.webhook-secret:}")
    private String webhookSecret;

    @Bean
    public PaymentProperties paymentProperties() {

        return new PaymentProperties(
                keyId,
                keySecret,
                webhookSecret
        );
    }

    /** Credentials for the active PaymentGateway. Blank values mean "not configured". */
    public record PaymentProperties(
            String keyId,
            String keySecret,
            String webhookSecret
    ) {}
}
