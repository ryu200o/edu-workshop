package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a refresh token ({@code iam_refresh_tokens}). Package-private and
 * confined to the outbound persistence adapter — an infrastructure detail separate from the
 * framework-free domain {@code io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken}.
 * The token row has no {@code @Version}: rotation/revocation is serialized by the pessimistic write
 * lock on {@code token_hash} (ADR 0015), and V21 has no version column (no migration V22, OQ-1).
 */
@Entity
@Table(name = "iam_refresh_tokens")
class RefreshTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenJpaEntity() {
        // required by JPA
    }

    RefreshTokenJpaEntity(UUID id, UUID userId, String tokenHash, Instant expiresAt,
                          Instant revokedAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
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

    Instant getRevokedAt() {
        return revokedAt;
    }

    void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
