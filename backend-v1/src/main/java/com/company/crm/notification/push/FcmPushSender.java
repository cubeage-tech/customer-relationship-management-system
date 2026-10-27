package com.company.crm.notification.push;

import org.springframework.beans.factory.annotation.Autowired;
import com.company.crm.integration.health.IntegrationHealthService;
import com.company.crm.integration.calllog.IntegrationCallLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FR-M.4: Firebase Cloud Messaging (HTTP v1 API), which delivers to Android directly and to iOS via
 * APNs. Authenticates with a Firebase service-account key file (app.push.fcm.credentials-file):
 * a JWT signed with its private key is exchanged for a short-lived OAuth token, cached until near
 * expiry. With no key file configured, push is off and notifications stay in the in-app inbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPushSender implements PushSender {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper objectMapper;

    /** INT-8: told when the provider rejects our credentials (optional so the class can be built without Spring). */
    @Autowired(required = false)
    private IntegrationHealthService integrationHealth;

    /** INT-9: every call is logged (optional, like the health tracker). */
    @Autowired(required = false)
    private IntegrationCallLog callLog;

    @Value("${app.push.fcm.credentials-file:}")
    private String credentialsFile;

    private record Account(String projectId, String clientEmail, PrivateKey key, String tokenUri) {}

    private Account account;
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    @Override
    public boolean isEnabled() {
        return credentialsFile != null && !credentialsFile.isBlank();
    }

    @Override
    public synchronized Result send(String token, String title, String body, Map<String, String> data) {
        if (!isEnabled()) {
            return Result.FAILED;
        }
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, "fcm", "push.send");
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("token", token);
            message.put("notification", Map.of("title", title, "body", body == null ? "" : body));
            message.put("data", data);
            message.put("android", Map.of("priority", "HIGH"));
            message.put("apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + account().projectId() + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("message", message))))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 300) {
                call.ok(response.statusCode());
                if (integrationHealth != null) {
                    integrationHealth.ok(IntegrationHealthService.FCM);
                }
                return Result.SENT;
            }
            String error = response.body();
            call.failed(response.statusCode(), error);
            // The app was uninstalled or the token rotated: FCM says NOT_FOUND / UNREGISTERED.
            if (response.statusCode() == 404 || error.contains("UNREGISTERED")
                    || (response.statusCode() == 400 && error.contains("registration token"))) {
                return Result.INVALID_TOKEN;
            }
            if (response.statusCode() == 401) {
                accessTokenExpiry = Instant.EPOCH; // refresh next time
            }
            if (response.statusCode() == 403 && integrationHealth != null) {
                integrationHealth.authFailed(IntegrationHealthService.FCM, "FCM refused the service account (HTTP 403)");
            }
            log.warn("FCM push failed ({}): {}", response.statusCode(), error.length() > 300 ? error.substring(0, 300) : error);
            // ERR-3: an expired token, throttling or a Firebase outage is worth another try.
            int status = response.statusCode();
            return status == 401 || status == 429 || status >= 500 ? Result.RETRY_LATER : Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.failed(null, "interrupted");
            return Result.RETRY_LATER;
        } catch (Exception e) {
            log.warn("FCM push error: {}", e.getMessage());
            call.failed(null, e.getMessage());
            return Result.RETRY_LATER; // network: Firebase unreachable
        }
    }

    private Account account() throws Exception {
        if (account == null) {
            JsonNode json = objectMapper.readTree(Files.readString(Path.of(credentialsFile)));
            String pem = json.path("private_key").asText()
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            account = new Account(json.path("project_id").asText(), json.path("client_email").asText(), key,
                    json.path("token_uri").asText("https://oauth2.googleapis.com/token"));
        }
        return account;
    }

    private String accessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(60))) {
            return accessToken;
        }
        Account a = account();
        Instant now = Instant.now();
        String assertion = Jwts.builder()
                .issuer(a.clientEmail())
                .subject(a.clientEmail())
                .audience().add(a.tokenUri()).and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(a.key(), Jwts.SIG.RS256)
                .compact();
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(a.tokenUri()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, "fcm", "oauth.token");
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            call.failed(null, e.getMessage());
            throw e;
        }
        if (response.statusCode() >= 300) {
            call.failed(response.statusCode(), "token request refused");
            // 400 invalid_grant / 401: the service-account key was revoked or deleted.
            if ((response.statusCode() == 400 || response.statusCode() == 401) && integrationHealth != null) {
                integrationHealth.authFailed(IntegrationHealthService.FCM, "Google rejected the service-account key (HTTP " + response.statusCode() + ")");
            }
            throw new IllegalStateException("Google OAuth token request failed with HTTP " + response.statusCode());
        }
        call.ok(response.statusCode());
        JsonNode json = objectMapper.readTree(response.body());
        accessToken = json.path("access_token").asText();
        accessTokenExpiry = now.plusSeconds(json.path("expires_in").asLong(3600));
        return accessToken;
    }
}
