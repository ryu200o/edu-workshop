package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

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
class JpaOneTimeTokenWriteAdapterTest {

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserId ownerId;

    @BeforeEach
    void seedUser() {
        User user = User.create(UserId.generate(), Email.of("ott-owner@example.com"), "hash", "A", Instant.now());
        UserJpaEntity entity = new UserJpaEntity(
                user.getId().value(), user.getEmail().value(), user.getPasswordHash(),
                user.getStatus().name(), user.getFullName(), user.getPhoneNumber(), user.getStudentCode(),
                user.getAvatarUrl(), user.isMustChangePassword(), user.getFailedLoginAttempts(),
                user.getLockoutCount(), user.getLockedUntil(), user.getLastLockedAt(),
                user.getCreatedAt(), user.getUpdatedAt());
        userJpaRepository.saveAndFlush(entity);
        ownerId = user.getId();
    }

    private OneTimeToken newToken() {
        Instant now = Instant.now();
        return OneTimeToken.create(ownerId, UUID.randomUUID().toString(), now.plusSeconds(3600), now);
    }

    @Test
    void save_thenLoadByHashWithLock_roundTrips() {
        OneTimeToken token = oneTimeTokenRepository.save(newToken());

        Optional<OneTimeToken> loaded = oneTimeTokenRepository.loadByHashWithLock(token.tokenHash());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().userId()).isEqualTo(ownerId);
        assertThat(loaded.get().isUsed()).isFalse();
    }

    @Test
    void markUsed_persistsUsedAt() {
        OneTimeToken token = oneTimeTokenRepository.save(newToken());
        Instant now = Instant.now();

        token.markUsed(now);
        oneTimeTokenRepository.save(token);

        OneTimeToken loaded = oneTimeTokenRepository.loadByHashWithLock(token.tokenHash()).orElseThrow();
        assertThat(loaded.isUsed()).isTrue();
        assertThat(loaded.isActive(now)).isFalse();
    }

    @Test
    void loadByHashWithLock_whenAbsent_returnsEmpty() {
        assertThat(oneTimeTokenRepository.loadByHashWithLock(UUID.randomUUID().toString())).isEmpty();
    }
}
