package com.company.crm.communication;

import com.company.crm.communication.integration.TwilioClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-5.2/5.3 webhooks: Twilio's documented scheme — Base64(HMAC-SHA1(token, url + sorted name+value pairs)). */
class TwilioClientSignatureTest {

    private static final String TOKEN = "test-auth-token";
    private static final String PATH = "/api/communications/webhooks/twilio/inbound";
    private final Map<String, String> params = Map.of("From", "whatsapp:+919876543210", "Body", "Hi", "MessageSid", "SM123");

    private TwilioClient client;

    @BeforeEach
    void setUp() {
        client = new TwilioClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "accountSid", "AC123");
        ReflectionTestUtils.setField(client, "authToken", TOKEN);
        ReflectionTestUtils.setField(client, "smsFrom", "");
        ReflectionTestUtils.setField(client, "whatsappFrom", "");
        ReflectionTestUtils.setField(client, "webhookBaseUrl", "https://api.example.com/");
    }

    @Test
    void acceptsTwilioSignature_rejectsTamperingAndMissingConfig() throws Exception {
        // Built by hand per Twilio docs: params sorted by name → Body, From, MessageSid.
        String data = "https://api.example.com" + PATH + "BodyHi" + "Fromwhatsapp:+919876543210" + "MessageSidSM123";
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

        assertThat(client.isValidSignature(PATH, params, signature)).isTrue();
        assertThat(client.isValidSignature(PATH, Map.of("From", "whatsapp:+919876543210", "Body", "Hacked", "MessageSid", "SM123"), signature)).isFalse();
        assertThat(client.isValidSignature(PATH, params, null)).isFalse();

        ReflectionTestUtils.setField(client, "authToken", "");
        assertThat(client.isValidSignature(PATH, params, signature)).isFalse();
    }
}
