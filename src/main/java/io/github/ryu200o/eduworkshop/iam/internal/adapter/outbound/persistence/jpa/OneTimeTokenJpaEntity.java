package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a one-time action token ({@code iam_password_reset_tokens}). The single
 * table backs both the verify-email and reset-password flows (OQ-1 RESOLVED — no purpose column;
 * the discriminator is the consuming Application handler). Package-private, confined to the outbound
 * persistence adapter. No {@code @Version} (V21 has none; single-use is serialized by the pessimistic
 * write lock on {@code token_hash}).
 */
@Entity
@Table(name = "iam_password_reset_tokens")
class OneTimeTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OneTimeTokenJpaEntity() {
        // required by JPA
    }

    OneTimeTokenJpaEntity(UUID id, UUID userId, String tokenHash, Instant expiresAt,
                          Instant usedAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getUsedAt() {
        return usedAt;
    }

    void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
