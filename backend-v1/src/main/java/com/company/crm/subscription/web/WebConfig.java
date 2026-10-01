package com.company.crm.subscription.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SubscriptionAccessInterceptor subscriptionAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(subscriptionAccessInterceptor)
                .addPathPatterns("/api/**")
                // A lapsed tenant must still be able to see its plan and pay to renew.
                .excludePathPatterns("/api/payments/**", "/api/subscriptions/**");
    }
}
