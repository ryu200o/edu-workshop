package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaUserWriteAdapterTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserRoleJpaRepository userRoleJpaRepository;

    private static User newUser() {
        Instant now = Instant.now();
        return User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash", "Nguyen Van A", now);
    }

    @Test
    void save_thenLoadById_roundTripsAggregate() {
        User saved = userRepository.save(newUser());

        Optional<User> loaded = userRepository.loadById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getEmail().value()).isEqualTo("student@example.com");
        assertThat(loaded.get().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(loaded.get().getRoles()).containsExactly(GlobalRole.USER);
    }

    @Test
    void loadById_whenAbsent_returnsEmpty() {
        assertThat(userRepository.loadById(UserId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void loadByEmail_reflectsPersistedRows() {
        User saved = userRepository.save(newUser());

        Optional<User> loaded = userRepository.loadByEmail(Email.of("STUDENT@Example.COM"));
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void existsByEmail_afterPersist_returnsTrue() {
        assertThat(userRepository.existsByEmail(Email.of("student@example.com"))).isFalse();

        userRepository.save(newUser());

        assertThat(userRepository.existsByEmail(Email.of("student@example.com"))).isTrue();
    }

    @Test
    void save_thenUpdateProfile_roundTrips() {
        User saved = userRepository.save(newUser());

        User loaded = userRepository.loadById(saved.getId()).orElseThrow();
        loaded.verifyEmail(Instant.now());
        loaded.updateProfile("Tran Thi B", "0901234567", "20001234", null, Instant.now());
        loaded.updateRoles(EnumSet.of(GlobalRole.USER, GlobalRole.AUDITOR), Instant.now());
        userRepository.save(loaded);

        User reloaded = userRepository.loadById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getFullName()).isEqualTo("Tran Thi B");
        assertThat(reloaded.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(reloaded.getStudentCode()).isEqualTo("20001234");
        assertThat(reloaded.getRoles()).containsExactlyInAnyOrder(GlobalRole.USER, GlobalRole.AUDITOR);
    }

    @Test
    void save_thenUpdate_keepsAndIncrementsOptimisticVersion() {
        User saved = userRepository.save(newUser());
        UserJpaEntity inserted = userJpaRepository.findById(saved.getId().value()).orElseThrow();
        Long versionAfterInsert = inserted.getVersion();

        User loaded = userRepository.loadById(saved.getId()).orElseThrow();
        loaded.verifyEmail(Instant.now());
        userRepository.save(loaded);

        UserJpaEntity updated = userJpaRepository.findById(saved.getId().value()).orElseThrow();
        assertThat(updated.getVersion()).isEqualTo(versionAfterInsert + 1L);
    }

    @Test
    void save_duplicateEmail_raceProofGate_throwsDuplicateEmailException() {
        userRepository.save(newUser());

        User duplicate = User.create(UserId.generate(), Email.of("student@example.com"),
                "$2a$12$hash", "Another User", Instant.now());

        assertThatThrownBy(() -> userRepository.save(duplicate))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("student@example.com");
    }

    @Test
    void loadByIdWithLock_returnsAggregate() {
        User saved = userRepository.save(newUser());

        Optional<User> locked = userRepository.loadByIdWithLock(saved.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(saved.getId());
    }
}
