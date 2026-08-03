package io.github.ryu200o.eduworkshop.registration.internal.application.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRescheduledIntegrationEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link WorkshopRescheduledEventHandler} — full Spring context + real H2 +
 * Flyway. Seeds registration rows via {@link RegistrationRepository}, then invokes the listener
 * directly (it runs in its own {@code REQUIRES_NEW} transaction) and asserts every active seat had
 * its {@code workshop_start_time} snapshot refreshed while keeping the {@code REGISTERED} status
 * (ADR 0007/0012).
 */
@SpringBootTest
class WorkshopRescheduledEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant OLD_START = Instant.parse("2026-09-01T09:00:00Z");
    // Danger zone: new start is 30h after reschedule (24h ≤ 30h < 36h) so the grace window is granted.
    private static final Instant NEW_START = NOW.plus(java.time.Duration.ofHours(30));

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private WorkshopRescheduledEventHandler handler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSchema() {
        jdbcTemplate.update("DELETE FROM registrations");
    }

    private void save(Registration registration) {
        transactionTemplate.executeWithoutResult(status -> registrationRepository.save(registration));
    }

    private Registration newRegistration(UUID workshopId) {
        return Registration.create(RegistrationId.generate(), StudentId.of(UUID.randomUUID()),
                WorkshopReference.of(workshopId, OLD_START), NOW);
    }

    @Test
    void handle_setsGracePeriodAndUpdatesStartTimeSnapshotForActiveSeats() {
        UUID workshop = UUID.randomUUID();
        save(newRegistration(workshop));
        save(newRegistration(workshop));
        // Cancelled seats must be left untouched (only REGISTERED get the grace window).
        Registration cancelled = newRegistration(workshop);
        cancelled.cancel(OLD_START.minusSeconds(24 * 3600 + 1));
        save(cancelled);

        Instant occurredAt = NOW;
        handler.handle(new WorkshopRescheduledIntegrationEvent(
                workshop, OLD_START, OLD_START.plusSeconds(7200), NEW_START, NEW_START.plusSeconds(7200), occurredAt));

        Instant graceUntil = occurredAt.plus(Registration.GRACE_PERIOD);

        assertThat(registrationRepository.loadAllByWorkshopIdAndState(workshop, RegistrationState.REGISTERED))
                .allMatch(r -> r.workshopReference().startTime().equals(NEW_START))
                .allMatch(r -> r.gracePeriodUntil().equals(graceUntil))
                .hasSize(2);
        assertThat(registrationRepository.loadAllByWorkshopIdAndState(workshop, RegistrationState.CANCELLED))
                .allMatch(r -> r.workshopReference().startTime().equals(OLD_START))
                .allMatch(r -> r.gracePeriodUntil() == null);
    }

    @Test
    void handle_doesNothingWhenWorkshopHasNoActiveSeats() {
        UUID workshop = UUID.randomUUID();

        handler.handle(new WorkshopRescheduledIntegrationEvent(
                workshop, OLD_START, OLD_START.plusSeconds(7200), NEW_START, NEW_START.plusSeconds(7200), NOW));

        assertThat(registrationRepository.loadAllByWorkshopIdAndState(workshop, RegistrationState.REGISTERED))
                .isEmpty();
    }
}
