package io.github.ryu200o.eduworkshop.registration.internal.application.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRescheduledIntegrationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link WorkshopRescheduledIntegrationEvent} (delivered via the transactional outbox) and
 * grants every active ({@code REGISTERED}) seat a 12-hour urgent cancellation grace window
 * (Titik 1), refreshing the {@code workshop_start_time} snapshot at the same time.
 *
 * <p>Cross-module collaboration per ADR 0010 / ADR 0011: the Registration module reacts to the
 * Workshop module's integration event — never a direct call and never a cross-module JOIN. Per ADR
 * 0007 the snapshot is refreshed via {@link Registration#grantGracePeriod(Instant, Instant, Instant)},
 * which also sets {@code gracePeriodUntil = occurredAt + 12h}. The {@code REGISTERED} status is
 * preserved (rescheduling notifies students, it does not cancel their seats).</p>
 *
 * <p>Runs {@code AFTER_COMMIT} in a new transaction ({@code REQUIRES_NEW}) so a failure here never
 * rolls back the business transaction; the outbox guarantees durable (re)delivery of the event.
 * Follows the 3-Phase Execution Pattern: (1) mutate domain + collect events, (2) batch persist via
 * {@code saveAll}, (3) batch publish events.</p>
 */
@Component
public class WorkshopRescheduledEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopRescheduledEventHandler.class);

    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    WorkshopRescheduledEventHandler(RegistrationRepository registrationRepository,
                                    RegistrationDomainEventPublisher registrationDomainEventPublisher,
                                    Clock clock) {
        this.registrationRepository = registrationRepository;
        this.registrationDomainEventPublisher = registrationDomainEventPublisher;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkshopRescheduledIntegrationEvent event) {
        Instant now = Instant.now(clock);
        List<Registration> active = registrationRepository.loadAllByWorkshopIdAndState(
                event.workshopId(), RegistrationState.REGISTERED);

        if (active.isEmpty()) {
            return;
        }

        log.info("Workshop {} rescheduled to {} — granting 12h grace window to {} active registration(s)",
                event.workshopId(), event.newStartTime(), active.size());

        // 1. Domain State Mutation & Event Collection
        List<RegistrationDomainEvent> allDomainEvents = new ArrayList<>();
        for (Registration registration : active) {
            registration.grantGracePeriod(event.occurredAt(), event.newStartTime(), now);
            allDomainEvents.addAll(registration.recordedEvents());
            registration.clearDomainEvents();
        }

        // 2. Batch Persistence (JDBC batching)
        registrationRepository.saveAll(active);

        // 3. Batch Event Publication
        registrationDomainEventPublisher.publish(allDomainEvents);
    }
}