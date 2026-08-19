package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed outbound adapter implementing the refresh-token write port. Managed-entity copy pattern
 * (ADR 0015 Strategy B) so the revocation mutation updates the same row; saveAndFlush surfaces any
 * constraint violation inside the transaction. Package-private; hidden inside the module's
 * {@code internal} boundary.
 */
@Component
class JpaRefreshTokenWriteAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    JpaRefreshTokenWriteAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = repository.findById(token.id())
                .map(existing -> copyTo(existing, token))
                .orElseGet(() -> toEntity(token));
        repository.saveAndFlush(entity);
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> loadByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(JpaRefreshTokenWriteAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> loadByHashWithLock(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash).map(JpaRefreshTokenWriteAdapter::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllActiveByUserId(UserId userId, Instant revokedAt) {
        repository.revokeAllActiveByUserId(userId.value(), revokedAt);
    }

    // ====================== MAPPER ======================

    private static RefreshTokenJpaEntity toEntity(RefreshToken token) {
        return new RefreshTokenJpaEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.expiresAt(),
                token.revokedAt(),
                token.createdAt()
        );
    }

    private static RefreshTokenJpaEntity copyTo(RefreshTokenJpaEntity entity, RefreshToken token) {
        entity.setRevokedAt(token.revokedAt());
        return entity;
    }

    private static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstruct(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt()
        );
    }
}
