package io.github.ryu200o.eduworkshop.registration.internal.application.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationRepository;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.Registration;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;
import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopCancelledIntegrationEvent;

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
 * Consumes {@link WorkshopCancelledIntegrationEvent} (delivered via the transactional outbox) and
 * flips every active ({@code REGISTERED}) seat for the cancelled workshop to {@code CANCELLED}.
 *
 * <p>Cross-module collaboration per ADR 0010 / ADR 0011: the Registration module reacts to the
 * Workshop module's integration event — never a direct call and never a cross-module JOIN. Because
 * the workshop is cancelled, the flip bypasses the 24-hour deadline (system-initiated, see
 * {@link Registration#cancelOnWorkshopCancelled(Instant)}).</p>
 *
 * <p>Runs {@code AFTER_COMMIT} in a new transaction ({@code REQUIRES_NEW}) so a failure here never
 * rolls back the business transaction; the outbox guarantees durable (re)delivery of the event.</p>
 */
@Component
public class WorkshopCancelledEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopCancelledEventHandler.class);

    private final RegistrationRepository registrationRepository;
    private final RegistrationDomainEventPublisher registrationDomainEventPublisher;
    private final Clock clock;

    WorkshopCancelledEventHandler(RegistrationRepository registrationRepository,
                                  RegistrationDomainEventPublisher registrationDomainEventPublisher,
                                  Clock clock) {
        this.registrationRepository = registrationRepository;
        this.registrationDomainEventPublisher = registrationDomainEventPublisher;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkshopCancelledIntegrationEvent event) {
        Instant now = Instant.now(clock);
        List<Registration> active = registrationRepository.loadAllByWorkshop(event.workshopId()).stream()
                .filter(r -> r.state() == RegistrationState.REGISTERED)
                .toList();

        log.info("Workshop {} cancelled — flipping {} active registration(s) to CANCELLED",
                event.workshopId(), active.size());

        for (Registration registration : active) {
            registration.cancelOnWorkshopCancelled(now);
            registrationRepository.save(registration);
            registrationDomainEventPublisher.publish(registration.recordedEvents());
            registration.clearDomainEvents();
        }
    }
}
