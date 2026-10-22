package com.company.crm.communication.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * FR-5.2 / FR-5.3: Twilio Programmable Messaging for WhatsApp and SMS, over plain HTTPS.
 * Disabled until TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and the sender numbers are set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwilioClient {

    public static final String STATUS_CALLBACK_PATH = "/api/communications/webhooks/twilio/status";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectMapper objectMapper;

    @Value("${app.communication.twilio.account-sid:}")
    private String accountSid;

    @Value("${app.communication.twilio.auth-token:}")
    private String authToken;

    @Value("${app.communication.twilio.sms-from:}")
    private String smsFrom;

    @Value("${app.communication.twilio.whatsapp-from:}")
    private String whatsappFrom;

    /** Public base URL of this API (e.g. https://api.example.com) — used for callbacks and signature checks. */
    @Value("${app.communication.twilio.webhook-base-url:}")
    private String webhookBaseUrl;

    /** Twilio's answer to a send: its message SID and initial status (usually "queued"). */
    public record SendResult(String sid, String status) {}

    /** Twilio rejected or could not be reached; the message is safe to show to the user. */
    public static class TwilioException extends RuntimeException {
        public TwilioException(String message) {
            super(message);
        }
    }

    public boolean isSmsEnabled() {
        return configured() && !smsFrom.isBlank();
    }

    public boolean isWhatsAppEnabled() {
        return configured() && !whatsappFrom.isBlank();
    }

    public SendResult sendSms(String toE164, String body) {
        return send(smsFrom, toE164, body);
    }

    public SendResult sendWhatsApp(String toE164, String body) {
        return send("whatsapp:" + whatsappFrom, "whatsapp:" + toE164, body);
    }

    private SendResult send(String from, String to, String body) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("From", from);
        form.put("To", to);
        form.put("Body", body);
        if (!webhookBaseUrl.isBlank()) {
            form.put("StatusCallback", trimSlash(webhookBaseUrl) + STATUS_CALLBACK_PATH);
        }
        String encoded = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        String auth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300) {
                String message = json.path("message").asText("Twilio returned HTTP " + response.statusCode());
                log.warn("Twilio send failed ({}): {}", response.statusCode(), message);
                throw new TwilioException(message);
            }
            return new SendResult(json.path("sid").asText(), json.path("status").asText("queued"));
        } catch (TwilioException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TwilioException("Message provider did not respond");
        } catch (Exception e) {
            log.warn("Twilio send error: {}", e.getMessage());
            throw new TwilioException("Message provider did not respond");
        }
    }

    /**
     * Twilio signs every webhook: Base64(HMAC-SHA1(authToken, fullUrl + each POST param name+value
     * sorted by name)). Without a configured token every webhook is rejected.
     */
    public boolean isValidSignature(String path, Map<String, String> params, String signature) {
        if (!configured() || webhookBaseUrl.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        StringBuilder data = new StringBuilder(trimSlash(webhookBaseUrl) + path);
        new TreeMap<>(params).forEach((k, v) -> data.append(k).append(v == null ? "" : v));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] expected = Base64.getEncoder().encode(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean configured() {
        return !accountSid.isBlank() && !authToken.isBlank();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
