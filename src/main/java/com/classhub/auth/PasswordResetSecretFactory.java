package com.classhub.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetSecretFactory {

    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetSecretFactory(
            @Value("${classhub.auth.password-reset.signing-key:classhub-local-password-reset-key-change-me}")
            String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String otp(UUID challengeId, Instant requestedAt) {
        byte[] digest = hmac("otp:" + challengeId + ":" + requestedAt.toEpochMilli());
        int value = ((digest[0] & 0xff) << 16) | ((digest[1] & 0xff) << 8) | (digest[2] & 0xff);
        return "%06d".formatted(value % 1_000_000);
    }

    public String newResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashOtp(UUID challengeId, String otp) {
        return hex(hmac("otp-hash:" + challengeId + ":" + otp));
    }

    public String hashResetToken(String token) {
        return hex(hmac("reset-token:" + token));
    }

    public boolean matchesOtp(PasswordResetChallenge challenge, String candidate) {
        return MessageDigest.isEqual(
                challenge.getOtpHash().getBytes(StandardCharsets.US_ASCII),
                hashOtp(challenge.getId(), candidate).getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Password reset secret generation failed", ex);
        }
    }

    private static String hex(byte[] value) { return HexFormat.of().formatHex(value); }
}
