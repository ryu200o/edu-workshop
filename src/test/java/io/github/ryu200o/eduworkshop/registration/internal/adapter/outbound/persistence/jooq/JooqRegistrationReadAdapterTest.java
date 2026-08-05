package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ registration read adapter — full Spring context + real H2
 * (PostgreSQL mode) + Flyway. Proves the read path counts only active (REGISTERED) rows directly
 * from flat SQL (no JPA entity, no domain reconstruction — CQRS bypass). Rows are seeded via
 * {@link RegistrationRepository} (JPA) since this adapter is read-only by design.
 */
@SpringBootTest
class JooqRegistrationReadAdapterTest {

    @Autowired
    private RegistrationReader registrationReader;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant END = START.plusSeconds(7200);
    private static final Instant REGISTERED_AT = Instant.parse("2026-08-01T10:00:00Z");

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM registrations");
    }

    private Registration newRegistration(UUID workshopId, UUID userId) {
        return Registration.create(RegistrationId.generate(), StudentId.of(userId),
                WorkshopReference.of(workshopId, START), REGISTERED_AT);
    }

    private Registration newRegistrationWithSnapshots(UUID workshopId, UUID userId, String title) {
        return Registration.create(RegistrationId.generate(), StudentId.of(userId),
                WorkshopReference.of(workshopId, START, title, END, "A-101"), REGISTERED_AT);
    }

    @Test
    void countActiveByWorkshop_countsOnlyRegisteredRows() {
        UUID workshop = UUID.randomUUID();
        UUID otherWorkshop = UUID.randomUUID();

        registrationRepository.save(newRegistration(workshop, UUID.randomUUID()));
        registrationRepository.save(newRegistration(workshop, UUID.randomUUID()));

        Registration cancelled = newRegistration(workshop, UUID.randomUUID());
        cancelled.cancel(Instant.parse("2026-08-31T09:00:00Z").minusSeconds(1));
        registrationRepository.save(cancelled);

        registrationRepository.save(newRegistration(otherWorkshop, UUID.randomUUID()));

        assertThat(registrationReader.countActiveByWorkshop(workshop)).isEqualTo(2);
        assertThat(registrationReader.countActiveByWorkshop(otherWorkshop)).isEqualTo(1);
        assertThat(registrationReader.countActiveByWorkshop(UUID.randomUUID())).isZero();
    }

    @Test
    void countActiveByWorkshop_whenEmpty_returnsZero() {
        assertThat(registrationReader.countActiveByWorkshop(UUID.randomUUID())).isZero();
    }

    @Test
    void getByUserId_noFilter_returnsAllRowsForThatUserOnly() {
        UUID workshop1 = UUID.randomUUID();
        UUID workshop2 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();

        registrationRepository.save(newRegistrationWithSnapshots(workshop1, userId, "Docker Essentials"));
        Registration cancelled = newRegistrationWithSnapshots(workshop2, userId, "K8s Deep Dive");
        cancelled.cancel(START.minus(Registration.CANCELLATION_DEADLINE).minusSeconds(1));
        registrationRepository.save(cancelled);
        registrationRepository.save(newRegistrationWithSnapshots(workshop1, otherUser, "Not mine"));

        List<MyRegistrationView> result = registrationReader.getByUserId(userId, null);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(v -> v.userId().equals(userId));
        assertThat(result).extracting(MyRegistrationView::workshopTitle)
                .containsExactlyInAnyOrder("Docker Essentials", "K8s Deep Dive");
        assertThat(result).extracting(MyRegistrationView::workshopStartTime).containsOnly(START);
        assertThat(result).extracting(MyRegistrationView::workshopEndTime).containsOnly(END);
        assertThat(result).extracting(MyRegistrationView::workshopRoomName).containsOnly("A-101");
    }

    @Test
    void getByUserId_withStatusFilter_returnsOnlyMatchingRows() {
        UUID workshop1 = UUID.randomUUID();
        UUID workshop2 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        registrationRepository.save(newRegistrationWithSnapshots(workshop1, userId, "Docker Essentials"));
        Registration cancelled = newRegistrationWithSnapshots(workshop2, userId, "K8s Deep Dive");
        cancelled.cancel(START.minus(Registration.CANCELLATION_DEADLINE).minusSeconds(1));
        registrationRepository.save(cancelled);

        List<MyRegistrationView> result =
                registrationReader.getByUserId(userId, MyRegistrationStatus.REGISTERED);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workshopTitle()).isEqualTo("Docker Essentials");
        assertThat(result.getFirst().status()).isEqualTo(MyRegistrationStatus.REGISTERED);
        assertThat(result.getFirst().cancelledAt()).isNull();
    }

    @Test
    void getByUserId_learnerWithNoBookings_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        registrationRepository.save(newRegistrationWithSnapshots(UUID.randomUUID(), UUID.randomUUID(), "Other"));

        assertThat(registrationReader.getByUserId(userId, null)).isEmpty();
    }
}
