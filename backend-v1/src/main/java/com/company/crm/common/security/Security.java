package com.company.crm.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless JWT security configuration. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class Security {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()
                        // Only the public pricing list — any other /api/plans endpoint needs a login.
                        .requestMatchers(HttpMethod.GET, "/api/plans/prices").permitAll()
                        // Server-to-server from the payment provider; authenticated by its signature.
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        // Twilio messaging webhooks (FR-5.2/5.3); authenticated by X-Twilio-Signature.
                        .requestMatchers(HttpMethod.POST, "/api/communications/webhooks/twilio/inbound",
                                "/api/communications/webhooks/twilio/status",
                                "/api/communications/webhooks/twilio/voice").permitAll()
                        // FR-4.5 customer view link; the secret token in the path is the credential.
                        .requestMatchers(HttpMethod.GET, "/api/public/quotations/*", "/api/public/quotations/*/pdf").permitAll()
                        // NAV-2: the customer answers from the same link; the token in the path is the credential.
                        .requestMatchers(HttpMethod.POST, "/api/public/quotations/*/respond").permitAll()
                        // FR-5.5c per-user calendar feed for calendar apps; the secret token is the credential.
                        .requestMatchers(HttpMethod.GET, "/api/public/calendar/*").permitAll()
                        // FR-6.1 support form / FR-6.5 feedback pages; the secret token in the path is the credential.
                        .requestMatchers("/api/public/support/*", "/api/public/feedback/*").permitAll()
                        // FR-7.4/7.1c campaign email links (open pixel, tracked links, unsubscribe); the recipient token is the credential.
                        .requestMatchers("/api/public/campaigns/**").permitAll()
                        // API-G12 integrations: provider webhooks (signature) and the ERP sync (X-Api-Key, checked per request).
                        .requestMatchers(HttpMethod.POST, "/api/integrations/payment", "/api/integrations/whatsapp-webhook").permitAll()
                        .requestMatchers("/api/integrations/erp/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Without this, Spring Security's default entry point returns 403 for a
                // missing/invalid token too — but the frontend's axios interceptor only
                // treats 401 as "please log in" and clears the session on that status.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(JsonSecurityErrors.unauthorized(objectMapper))
                        .accessDeniedHandler(JsonSecurityErrors.forbidden(objectMapper))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
