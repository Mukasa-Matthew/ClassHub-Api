package com.classhub.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClassRepSetupTokenFactory {

    private final byte[] signingKey;

    public ClassRepSetupTokenFactory(
            @Value("${classhub.auth.class-rep-setup.signing-key:classhub-local-development-key-change-me}")
            String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    public String create(UUID issuanceId, Instant issuedAt) {
        byte[] idBytes = ByteBuffer.allocate(16)
                .putLong(issuanceId.getMostSignificantBits())
                .putLong(issuanceId.getLeastSignificantBits())
                .array();
        String idPart = Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
        String payload = idPart + "." + issuedAt.toEpochMilli();
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
        return payload + "." + signature;
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate setup token", ex);
        }
    }
}
