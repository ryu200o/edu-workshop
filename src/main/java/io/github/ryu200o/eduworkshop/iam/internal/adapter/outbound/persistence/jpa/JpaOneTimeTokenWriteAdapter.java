package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA-backed outbound adapter implementing the one-time-token write port. Managed-entity copy
 * pattern (ADR 0015 Strategy B) so consumption updates the same row; saveAndFlush surfaces any
 * constraint violation inside the transaction. Package-private; hidden inside the module's
 * {@code internal} boundary.
 */
@Component
class JpaOneTimeTokenWriteAdapter implements OneTimeTokenRepository {

    private final OneTimeTokenJpaRepository repository;

    JpaOneTimeTokenWriteAdapter(OneTimeTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OneTimeToken save(OneTimeToken token) {
        OneTimeTokenJpaEntity entity = repository.findById(token.id())
                .map(existing -> copyTo(existing, token))
                .orElseGet(() -> toEntity(token));
        repository.saveAndFlush(entity);
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OneTimeToken> loadByHashWithLock(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash).map(JpaOneTimeTokenWriteAdapter::toDomain);
    }

    // ====================== MAPPER ======================

    private static OneTimeTokenJpaEntity toEntity(OneTimeToken token) {
        return new OneTimeTokenJpaEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.expiresAt(),
                token.usedAt(),
                token.createdAt()
        );
    }

    private static OneTimeTokenJpaEntity copyTo(OneTimeTokenJpaEntity entity, OneTimeToken token) {
        entity.setUsedAt(token.usedAt());
        return entity;
    }

    private static OneTimeToken toDomain(OneTimeTokenJpaEntity entity) {
        return OneTimeToken.reconstruct(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedAt()
        );
    }
}
