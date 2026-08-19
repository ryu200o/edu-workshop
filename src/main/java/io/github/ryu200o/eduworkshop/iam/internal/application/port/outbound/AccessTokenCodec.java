package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port (SPI) for encoding/decoding the access-token JWT. Lives in the Application layer so
 * the auth handlers can issue a session on login/refresh without knowing the JWT implementation; the
 * implementation (Nimbus, HS256) is an inbound adapter detail in {@code adapter/inbound/security}
 * (plan §2.2, §5).
 *
 * <p>Only the claims the consuming modules need are carried (ADR 0020 §1.1): {@code sub} = opaque
 * userId, {@code email}, global {@code roles}, the {@code must_change_password} flag, plus the
 * standard {@code iat}/{@code exp} (validated by the decoder). Expiry is enforced during
 * {@link #decode(String)} — an expired or tampered token surfaces as {@link InvalidTokenException}.</p>
 */
public interface AccessTokenCodec {

    /**
     * Encodes the given claims into a signed JWT string.
     */
    String encode(AccessTokenClaims claims);

    /**
     * Decodes and verifies a JWT (signature + {@code exp} timestamp). Throws
     * {@link InvalidTokenException} for any malformed, tampered, or expired token.
     */
    AuthenticatedPrincipal decode(String token);

    /**
     * Claims embedded in the access token. {@code roles} are the global RBAC role names
     * ({@code USER}/{@code ADMIN}/...) as plain strings — kept framework/domain-free on purpose.
     *
     * @param userId              the opaque user id ({@code sub})
     * @param email               the normalized login email
     * @param roles               the global RBAC role names
     * @param mustChangePassword  the {@code must_change_password} gate
     * @param issuedAt            token issuance instant ({@code iat})
     * @param expiresAt           token expiry instant ({@code exp})
     */
    record AccessTokenClaims(
            UUID userId,
            String email,
            Set<String> roles,
            boolean mustChangePassword,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
