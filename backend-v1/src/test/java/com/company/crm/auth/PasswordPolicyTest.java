package com.company.crm.auth;

import com.company.crm.auth.password.BreachedPasswordChecker;
import com.company.crm.auth.password.PasswordPolicy;
import com.company.crm.common.exception.ApiException;
import com.company.crm.integration.calllog.IntegrationCallLog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** SEC-1b: the password rules, and the breach check sending only a 5-character hash prefix. */
class PasswordPolicyTest {

    private final BreachedPasswordChecker breached = new BreachedPasswordChecker(); // off unless a test turns it on
    private final PasswordPolicy policy = new PasswordPolicy(mock(PasswordEncoder.class), mock(JdbcTemplate.class), breached);

    @Test
    void weakPasswordsAreRefusedWithTheRuleTheyBreak() {
        assertRefused("Short1!x", "Use at least 10 characters");
        assertRefused("alllowercaseletters", "Mix at least three of: lowercase letters, capitals, numbers and symbols");
        assertRefused("Aa1!Aa1!Aa1!", "Use more different characters");
        assertRefused("Welcome@123", "This password is too common. Choose a different one");
        assertRefused("Priya#Sales2026", "Don't use your name or email address in the password");
        assertRefused("Xy!" + "kumar.p" + "2026", "Don't use your name or email address in the password");
        assertThatCode(() -> policy.check("Blue#Kettle42", "kumar.p@crm.test", "Priya Sharma", null)).doesNotThrowAnyException();
    }

    @Test
    void theBreachCheckSendsOnlyAHashPrefixAndMatchesLocally() throws Exception {
        // SHA-1("Blue#Kettle42") is looked up by its first 5 hex characters; the server answers suffixes.
        String hash = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-1").digest("Blue#Kettle42".getBytes(StandardCharsets.UTF_8)));
        AtomicReference<String> asked = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/range/", exchange -> {
            asked.set(exchange.getRequestURI().getPath() + " padding=" + exchange.getRequestHeaders().getFirst("Add-Padding"));
            byte[] body = ("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + hash.substring(5) + ":42\r\nFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ReflectionTestUtils.setField(breached, "enabled", true);
            ReflectionTestUtils.setField(breached, "rangeUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/range/");
            IntegrationCallLog callLog = new IntegrationCallLog(mock(JdbcTemplate.class));
            ReflectionTestUtils.setField(breached, "callLog", callLog);

            assertThat(breached.isBreached("Blue#Kettle42")).isTrue();
            assertThat(asked.get()).isEqualTo("/range/" + hash.substring(0, 5) + " padding=true");
            assertThat(breached.isBreached("Another#Kettle43")).isFalse();
            assertThatThrownBy(() -> policy.check("Blue#Kettle42", "a@b.test", "Owner", null))
                    .isInstanceOf(ApiException.class).hasMessageContaining("data breach");
        } finally {
            server.stop(0);
        }
        // Unreachable: the password isn't blocked.
        assertThat(breached.isBreached("Blue#Kettle42")).isFalse();
    }

    private void assertRefused(String password, String message) {
        assertThatThrownBy(() -> policy.check(password, "kumar.p@crm.test", "Priya Sharma", null))
                .isInstanceOf(ApiException.class)
                .hasMessage(message)
                .extracting("errorCode").isEqualTo("WEAK_PASSWORD");
    }
}
