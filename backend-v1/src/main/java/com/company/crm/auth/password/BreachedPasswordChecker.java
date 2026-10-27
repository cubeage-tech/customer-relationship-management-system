package com.company.crm.auth.password;

import com.company.crm.integration.calllog.IntegrationCallLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * SEC-1b: asks Have I Been Pwned whether a password appears in known data breaches. Only the first
 * 5 characters of the password's SHA-1 hash leave the CRM (k-anonymity), with padded answers; the
 * match is made here. If the service can't be reached the password isn't blocked.
 */
@Component
@Slf4j
public class BreachedPasswordChecker {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${app.security.password.breach-check:true}")
    private boolean enabled;

    @Value("${app.security.password.breach-check-url:https://api.pwnedpasswords.com/range/}")
    private String rangeUrl;

    /** INT-9 (optional so the class can be built without Spring). */
    @Autowired(required = false)
    private IntegrationCallLog callLog;

    public boolean isEnabled() {
        return enabled;
    }

    /** True only when the breach list has this password; false when off or unreachable. */
    public boolean isBreached(String password) {
        if (!enabled || password == null) {
            return false;
        }
        String hash = sha1(password);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);
        HttpRequest request = HttpRequest.newBuilder(URI.create(rangeUrl + prefix))
                .timeout(Duration.ofSeconds(3))
                .header("Add-Padding", "true")
                .header("User-Agent", "SmartCRM-password-check")
                .GET()
                .build();
        IntegrationCallLog.Call call = IntegrationCallLog.start(callLog, "hibp", "password.range");
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                call.failed(response.statusCode(), "range lookup refused");
                log.warn("Breached-password check answered HTTP {}; password not blocked", response.statusCode());
                return false;
            }
            call.ok(response.statusCode());
            for (String line : response.body().split("\\r?\\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                    return !"0".equals(line.substring(colon + 1).trim()); // padding entries have a count of 0
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.failed(null, "interrupted");
            return false;
        } catch (Exception e) {
            call.failed(null, e.getMessage());
            log.warn("Breached-password check unavailable ({}); password not blocked", e.getMessage());
            return false;
        }
    }

    private static String sha1(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
