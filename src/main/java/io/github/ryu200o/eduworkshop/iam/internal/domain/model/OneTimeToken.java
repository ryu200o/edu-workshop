package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-use action token shared by the verify-email and forgot-password/reset-password flows
 * (OQ-1 RESOLVED, plan §2.1 / §1.2). The same table ({@code iam_password_reset_tokens}) backs both
 * purposes; there is intentionally no {@code purpose} column — the discriminator lives in the
 * Application handler that consumes the token.
 *
 * <p>Only the SHA-256 hash of the opaque random token is persisted ({@code token_hash}); the raw
 * value is returned to the client exactly once at issuance. A token is {@code active} while not yet
 * used and not past {@code expiresAt}. Single-use is enforced at the Application layer by loading
 * under a pessimistic write lock before marking used (ADR 0015 set-based-style serialization).</p>
 */
public final class OneTimeToken {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;
    private final Instant createdAt;

    private OneTimeToken(UUID id, UserId userId, String tokenHash, Instant expiresAt,
                         Instant usedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.usedAt = usedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Issues a new one-time action token for the given user. {@code expiresAt} must be in the future.
     */
    public static OneTimeToken create(UserId userId, String tokenHash, Instant expiresAt, Instant now) {
        Objects.requireNonNull(now, "now");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("OneTimeToken expiresAt must be in the future");
        }
        return new OneTimeToken(UUID.randomUUID(), userId, tokenHash, expiresAt, null, now);
    }

    /**
     * Reconstitution path for the persistence adapter — bypasses all invariant checks.
     */
    public static OneTimeToken reconstruct(UUID id, UserId userId, String tokenHash, Instant expiresAt,
                                           Instant usedAt, Instant createdAt) {
        return new OneTimeToken(id, userId, tokenHash, expiresAt, usedAt, createdAt);
    }

    /**
     * Consumes the token (single-use). Idempotent.
     */
    public void markUsed(Instant now) {
        Objects.requireNonNull(now, "now");
        if (usedAt == null) {
            this.usedAt = now;
        }
    }

    /**
     * {@code true} while the token is still valid: not used and not past its expiry.
     */
    public boolean isActive(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
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

    public Instant usedAt() {
        return usedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
