package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ read adapter — full Spring context + real H2 (PostgreSQL mode) +
 * Flyway. Proves the read path assembles the {@code UserSummaryView} projection directly from flat
 * SQL columns (no JPA entity, no domain reconstruction — CQRS bypass). Rows are seeded via
 * {@link UserRepository} (JPA) since this adapter is read-only by design.
 */
@SpringBootTest
class JooqUserReadAdapterTest {

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM iam_refresh_tokens");
        jdbcTemplate.update("DELETE FROM iam_password_reset_tokens");
        jdbcTemplate.update("DELETE FROM iam_user_roles");
        jdbcTemplate.update("DELETE FROM iam_users");
    }

    private static User newUser() {
        Instant now = Instant.now();
        return User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash", "Nguyen Van A", now);
    }

    @Test
    void save_thenGetById_roundTripsThroughDatabase() {
        User user = userRepository.save(newUser());

        Optional<UserSummaryView> found = userReader.getById(user.getId());

        assertThat(found).isPresent();
        UserSummaryView view = found.get();
        assertThat(view.id()).isEqualTo(user.getId().value());
        assertThat(view.email()).isEqualTo("student@example.com");
        assertThat(view.fullName()).isEqualTo("Nguyen Van A");
        assertThat(view.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(view.roles()).containsExactly("USER");
    }

    @Test
    void getById_includesUpdatedRoles() {
        User user = userRepository.save(newUser());
        User loaded = userRepository.loadById(user.getId()).orElseThrow();
        loaded.verifyEmail(Instant.now());
        loaded.updateRoles(EnumSet.of(GlobalRole.USER, GlobalRole.PLANNER), Instant.now());
        userRepository.save(loaded);

        Optional<UserSummaryView> found = userReader.getById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().roles()).containsExactlyInAnyOrder("USER", "PLANNER");
        assertThat(found.get().status()).isEqualTo("ACTIVE");
    }

    @Test
    void getById_whenAbsent_returnsEmpty() {
        assertThat(userReader.getById(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
