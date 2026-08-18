package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token material utility shared by the auth handlers (plan §2.2): generates opaque raw tokens
 * (256-bit, base64url, no padding) and their persisted SHA-256 hex digests. Package-private —
 * lives next to the handlers it serves, never exposed outside the Application layer.
 */
final class TokenHash {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHash() {
        // utility class
    }

    /**
     * Generates a cryptographically random opaque token (32 bytes → base64url without padding).
     */
    static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex digest of the raw token — the only form stored in the database.
     */
    static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
