package com.company.crm.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * NFR-7.2b: encrypts personal contact data before it is stored. AES-256-GCM with a random IV per
 * value; stored as {@code enc:v1:<base64(iv | ciphertext+tag)>}. Values without the prefix are
 * read as-is (rows written before encryption, until the startup backfill encrypts them).
 *
 * <p>Encrypted values can't be matched in SQL, so fields looked up by value also store a keyed
 * HMAC-SHA256 "blind index" (*_hash columns). Encryption and index keys are derived from one
 * master key, DATA_ENCRYPTION_KEY (base64 of 32 random bytes), which the app requires to start.
 * Losing that key makes the encrypted data unreadable — keep it in the deployment's secret store.
 */
@Component("dataEncryption")
public class DataEncryption {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile DataEncryption instance;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec indexKey;

    public DataEncryption(@Value("${app.security.data-encryption-key:}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY is not set. It encrypts personal data at rest (NFR-7.2b); "
                    + "generate one with `openssl rand -base64 32`, set it in the environment and keep it safe.");
        }
        byte[] master;
        try {
            master = Base64.getDecoder().decode(masterKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY must be base64 (e.g. from `openssl rand -base64 32`)");
        }
        if (master.length != 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY must decode to 32 bytes (got " + master.length + ")");
        }
        encryptionKey = new SecretKeySpec(derive(master, "smartcrm/pii/encryption"), "AES");
        indexKey = new SecretKeySpec(derive(master, "smartcrm/pii/index"), "HmacSHA256");
        instance = this;
    }

    /** For JPA converters and entity listeners, which Hibernate creates itself. */
    public static DataEncryption get() {
        DataEncryption current = instance;
        if (current == null) {
            throw new IllegalStateException("DataEncryption is not initialised yet");
        }
        return current;
    }

    public String encrypt(String plain) {
        if (plain == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a value", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key or tampered value: fail loudly rather than show garbage.
            throw new IllegalStateException("Could not decrypt a stored value — is DATA_ENCRYPTION_KEY the key it was written with?", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Blind index of an email address (case-insensitive), or null when blank. */
    public String emailIndex(String email) {
        return email == null || email.isBlank() ? null : index("email:" + email.trim().toLowerCase());
    }

    /** Blind index of a phone number by its last 10 digits, as inbound calls/SMS are matched; null if shorter. */
    public String phoneIndex(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 10 ? null : index("phone:" + digits.substring(digits.length() - 10));
    }

    /** Blind index of a message counterpart exactly as stored (normalized email or E.164 number). */
    public String counterpartIndex(String counterpart) {
        return counterpart == null || counterpart.isBlank() ? null : index("counterpart:" + counterpart.trim().toLowerCase());
    }

    private String index(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not index a value", e);
        }
    }

    /** SHA-256 hex of a link token: only the hash is stored (NFR-7.2b). */
    public static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] derive(byte[] master, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The keys must exist before JPA loads or saves any entity (e.g. startup seeders). */
    @Configuration(proxyBeanMethods = false)
    static class EntityManagerFactoryDependsOnDataEncryption extends EntityManagerFactoryDependsOnPostProcessor {
        EntityManagerFactoryDependsOnDataEncryption() {
            super("dataEncryption");
        }
    }
}
