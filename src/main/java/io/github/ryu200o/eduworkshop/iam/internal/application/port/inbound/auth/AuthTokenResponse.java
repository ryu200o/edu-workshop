package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth;

/**
 * Session issued by the {@link AuthTokenUseCase}: an access JWT plus a raw refresh token.
 *
 * <p>The refresh token is returned to the client exactly once; only its SHA-256 hash is persisted.
 * {@code mustChangePassword} lets the client branch to the change-password flow before using
 * business APIs (ADR 0020).</p>
 *
 * @param accessToken        the signed access JWT (Bearer)
 * @param refreshToken       the RAW opaque refresh token (returned once; only its hash is stored)
 * @param expiresInSeconds   access-token lifetime in seconds (client caching hint)
 * @param mustChangePassword whether the caller must change the password before using business APIs
 */
public record AuthTokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                boolean mustChangePassword) {
}