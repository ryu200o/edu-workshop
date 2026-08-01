package io.github.ryu200o.eduworkshop.registration.internal.application.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopCancelledIntegrationEvent;

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
 * Integration test for {@link WorkshopCancelledEventHandler} — full Spring context + real H2 +
 * Flyway. Seeds registration rows via {@link RegistrationRepository}, then invokes the listener
 * directly (it runs in its own {@code REQUIRES_NEW} transaction) and asserts every active seat was
 * flipped to {@code CANCELLED} regardless of the 24-hour deadline.
 */
@SpringBootTest
class WorkshopCancelledEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    // Workshop starts 2026-09-01 09:00 UTC → 24h cancellation deadline = 2026-08-31 09:00 UTC.
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    // A time safely BEFORE the deadline, so the seed can cancel() a row via the student path.
    private static final Instant BEFORE_DEADLINE = Instant.parse("2026-08-31T08:00:00Z");

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private WorkshopCancelledEventHandler handler;

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
                WorkshopReference.of(workshopId, START), NOW);
    }

    @Test
    void handle_flipsAllActiveSeatsToCancelledRegardlessOfDeadline() {
        UUID workshop = UUID.randomUUID();
        save(newRegistration(workshop));
        save(newRegistration(workshop));
        // Already cancelled seats must be left untouched.
        Registration cancelled = newRegistration(workshop);
        cancelled.cancel(BEFORE_DEADLINE);
        save(cancelled);

        handler.handle(new WorkshopCancelledIntegrationEvent(workshop, NOW));

        assertThat(registrationRepository.loadAllByWorkshop(workshop))
                .allMatch(r -> r.state() == RegistrationState.CANCELLED)
                .hasSize(3);
    }

    @Test
    void handle_flipsNoRowsWhenWorkshopHasNoActiveSeats() {
        UUID workshop = UUID.randomUUID();

        handler.handle(new WorkshopCancelledIntegrationEvent(workshop, NOW));

        assertThat(registrationRepository.loadAllByWorkshop(workshop)).isEmpty();
    }
}
