package io.github.ryu200o.eduworkshop.registration.internal.adapter.driven.persistence.jooq;

import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.out.RegistrationReader;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.out.RegistrationRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JOOQ registration read adapter — full Spring context + real H2
 * (PostgreSQL mode) + Flyway. Proves the read path counts only active (REGISTERED) rows directly
 * from flat SQL (no JPA entity, no domain reconstruction — CQRS bypass). Rows are seeded via
 * {@link RegistrationRepository} (JPA) since this adapter is read-only by design. Also verifies the
 * {@link RegistrationExposeAPI} Module Facade delegates to the same count.
 */
@SpringBootTest
class JooqRegistrationReadAdapterTest {

    @Autowired
    private RegistrationReader registrationReader;

    @Autowired
    private RegistrationExposeAPI registrationExposeApi;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM registrations");
    }

    private Registration newRegistration(UUID workshopId, UUID userId) {
        return Registration.create(RegistrationId.generate(), StudentId.of(userId),
                WorkshopReference.of(workshopId, Instant.parse("2026-09-01T09:00:00Z")),
                Instant.parse("2026-08-01T10:00:00Z"));
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
    void exposeApi_delegatesToTheSameCount() {
        UUID workshop = UUID.randomUUID();
        registrationRepository.save(newRegistration(workshop, UUID.randomUUID()));
        registrationRepository.save(newRegistration(workshop, UUID.randomUUID()));

        assertThat(registrationExposeApi.countActiveRegistrations(workshop)).isEqualTo(2);
        assertThat(registrationExposeApi.countActiveRegistrations(UUID.randomUUID())).isZero();
    }
}
