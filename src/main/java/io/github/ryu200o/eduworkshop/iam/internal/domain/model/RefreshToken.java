package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stateful refresh-token entity for the auth session (ADR 0020 §1.4, plan §2.3).
 *
 * <p>Only the SHA-256 hash of the opaque random token is persisted ({@code token_hash}); the raw
 * value is returned to the client exactly once at issuance. One row per issued token. {@code active}
 * means not yet revoked and not past {@code expiresAt}. Rotation (RTR): a refresh consumes its own
 * token (mark {@code revoked}) and issues a fresh pair; re-use of an already-revoked token triggers
 * a whole-family revoke at the Application layer (OQ-3 RESOLVED).</p>
 *
 * <p>Local invariant only: expiry/revocation state transitions. Cross-user policies (revoke-all on
 * password change / account disable, OQ-3) are orchestrated by the Application layer (ADR 0005).</p>
 */
public final class RefreshToken {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final Instant createdAt;

    private RefreshToken(UUID id, UserId userId, String tokenHash, Instant expiresAt,
                         Instant revokedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.revokedAt = revokedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Issues a new refresh token for the given user. The {@code tokenHash} is the SHA-256 digest of
     * the opaque raw token; the raw value must have been handed to the client by the caller.
     *
     * @throws IllegalArgumentException if {@code expiresAt} is not in the future
     */
    public static RefreshToken create(UserId userId, String tokenHash, Instant expiresAt, Instant now) {
        Objects.requireNonNull(now, "now");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("RefreshToken expiresAt must be in the future");
        }
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt, null, now);
    }

    /**
     * Reconstitution path for the persistence adapter — bypasses all invariant checks (no spurious
     * re-validation on read, AGENTS.md). Framework-free: the entity carries no ORM annotations.
     */
    public static RefreshToken reconstruct(UUID id, UserId userId, String tokenHash, Instant expiresAt,
                                           Instant revokedAt, Instant createdAt) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revokedAt, createdAt);
    }

    /**
     * Marks the token as revoked (rotation). Idempotent.
     */
    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now");
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    /**
     * {@code true} while the token is still valid: not revoked and not past its expiry.
     */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
