package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading {@link RefreshToken} entities on the write side.
 * Write ports use {@code load*} naming (ADR 0016). Lookups are by the persisted SHA-256
 * {@code token_hash} (never by the raw token, which is not stored).
 */
public interface RefreshTokenRepository {

    /**
     * Persists a refresh token (issuance) or its mutated state (revocation). saveAndFlush() per
     * ADR 0015 Golden Rule 1.
     */
    RefreshToken save(RefreshToken token);

    /**
     * Loads a refresh token by its hash without locking (used by read-only paths).
     */
    Optional<RefreshToken> loadByHash(String tokenHash);

    /**
     * Loads a refresh token by its hash under a {@code SELECT ... FOR UPDATE} pessimistic write lock
     * (ADR 0015). Serializes rotation (RTR) so two concurrent refreshes of the same token cannot both
     * succeed.
     */
    Optional<RefreshToken> loadByHashWithLock(String tokenHash);

    /**
     * Revokes every still-active refresh token of a user in a single bulk update (OQ-3 RESOLVED,
     * RFC 6819 family protection). Called when a revoked token is replayed, when the account is
     * disabled/locked, or after a password reset.
     */
    void revokeAllActiveByUserId(UserId userId, Instant revokedAt);
}
