package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaRefreshTokenWriteAdapterTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserId ownerId;

    @BeforeEach
    void seedUser() {
        User user = User.create(UserId.generate(), Email.of("token-owner@example.com"), "hash", "A", Instant.now());
        UserJpaEntity entity = new UserJpaEntity(
                user.getId().value(), user.getEmail().value(), user.getPasswordHash(),
                user.getStatus().name(), user.getFullName(), user.getPhoneNumber(), user.getStudentCode(),
                user.getAvatarUrl(), user.isMustChangePassword(), user.getFailedLoginAttempts(),
                user.getLockoutCount(), user.getLockedUntil(), user.getLastLockedAt(),
                user.getCreatedAt(), user.getUpdatedAt());
        userJpaRepository.saveAndFlush(entity);
        ownerId = user.getId();
    }

    private RefreshToken newToken() {
        Instant now = Instant.now();
        return RefreshToken.create(ownerId, UUID.randomUUID().toString(), now.plusSeconds(3600), now);
    }

    @Test
    void save_thenLoadByHash_roundTrips() {
        RefreshToken token = refreshTokenRepository.save(newToken());

        Optional<RefreshToken> loaded = refreshTokenRepository.loadByHash(token.tokenHash());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().userId()).isEqualTo(ownerId);
        assertThat(loaded.get().isRevoked()).isFalse();
    }

    @Test
    void revoke_persistsRevokedAt() {
        RefreshToken token = refreshTokenRepository.save(newToken());
        Instant now = Instant.now();

        token.revoke(now);
        refreshTokenRepository.save(token);

        assertThat(refreshTokenRepository.loadByHash(token.tokenHash()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.loadByHash(token.tokenHash()).orElseThrow().isActive(now)).isFalse();
    }

    @Test
    void revokeAllActiveByUserId_keepsRevokedTokensUntouched() {
        Instant now = Instant.now();
        RefreshToken active = refreshTokenRepository.save(newToken());
        RefreshToken revoked = refreshTokenRepository.save(newToken());
        revoked.revoke(now);
        refreshTokenRepository.save(revoked);

        refreshTokenRepository.revokeAllActiveByUserId(ownerId, now);

        // The bulk UPDATE bypasses the persistence context, so evict the cached copies first.
        entityManager.clear();
        assertThat(refreshTokenRepository.loadByHash(active.tokenHash()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.loadByHash(revoked.tokenHash()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void loadByHash_whenAbsent_returnsEmpty() {
        assertThat(refreshTokenRepository.loadByHash(UUID.randomUUID().toString())).isEmpty();
    }
}
