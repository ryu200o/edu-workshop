package io.github.ryu200o.eduworkshop.registration.internal.application.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.WorkshopReference;
import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopRescheduledIntegrationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Consumes {@link WorkshopRescheduledIntegrationEvent} (delivered via the transactional outbox) and
 * refreshes the {@code workshop_start_time} snapshot of every active ({@code REGISTERED}) seat for
 * the rescheduled workshop.
 *
 * <p>Cross-module collaboration per ADR 0010 / ADR 0011: the Registration module reacts to the
 * Workshop module's integration event — never a direct call and never a cross-module JOIN. Per ADR
 * 0007 the snapshot is refreshed, the {@code REGISTERED} status is preserved (rescheduling notifies
 * students, it does not cancel their seats), and no registration domain event is emitted
 * (projection refresh only).</p>
 *
 * <p>Runs {@code AFTER_COMMIT} in a new transaction ({@code REQUIRES_NEW}) so a failure here never
 * rolls back the business transaction; the outbox guarantees durable (re)delivery of the event.</p>
 */
@Component
public class WorkshopRescheduledEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopRescheduledEventHandler.class);

    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    WorkshopRescheduledEventHandler(RegistrationRepository registrationRepository,
                                    Clock clock) {
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkshopRescheduledIntegrationEvent event) {
        Instant now = Instant.now(clock);
        List<Registration> active = registrationRepository.loadAllByWorkshop(event.workshopId()).stream()
                .filter(r -> r.state() == RegistrationState.REGISTERED)
                .toList();

        log.info("Workshop {} rescheduled to {} — refreshing start-time snapshot of {} active registration(s)",
                event.workshopId(), event.newStartTime(), active.size());

        WorkshopReference updatedRef = WorkshopReference.of(event.workshopId(), event.newStartTime());
        for (Registration registration : active) {
            registration.refreshWorkshopStartTime(updatedRef, now);
            registrationRepository.save(registration);
            registration.clearDomainEvents();
        }
    }
}
