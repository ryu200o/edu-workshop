package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopCancelledIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopRescheduledIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopIntegrationEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopRescheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to Workshop domain events and maps them into cross-module integration events (contract
 * level). Only events that have an actual consumer in another module are published (YAGNI):
 * {@link WorkshopCancelled} → {@link WorkshopCancelledIntegrationEvent} (Registration flips active
 * seats) and {@link WorkshopRescheduled} → {@link WorkshopRescheduledIntegrationEvent} (Registration
 * refreshes its start-time snapshot). All other domain events have no cross-module consumer yet and
 * are skipped. Runs {@code AFTER_COMMIT} in a new transaction, so a failed mapping never rolls back
 * the business transaction (ADR 0011 outbox guarantees durable delivery).
 */
@Component
class WorkshopDomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkshopDomainEventListener.class);

    private final WorkshopIntegrationEventPublisher publisher;

    WorkshopDomainEventListener(WorkshopIntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishIntegrationEvent(WorkshopDomainEvent event) {
        WorkshopIntegrationEvent integration = switch (event) {
            case WorkshopCancelled e -> map(e);
            case WorkshopRescheduled e -> map(e);
            default -> null;
        };
        if (integration == null) {
            log.debug("Workshop domain event {} has no integration event — skipping", event.getClass().getSimpleName());
            return;
        }
        log.debug("Publishing integration event: {}", integration);
        publisher.publish(integration);
    }

    private static WorkshopCancelledIntegrationEvent map(WorkshopCancelled e) {
        return new WorkshopCancelledIntegrationEvent(
                e.workshopId().value(),
                e.occurredAt()
        );
    }

    private static WorkshopRescheduledIntegrationEvent map(WorkshopRescheduled e) {
        return new WorkshopRescheduledIntegrationEvent(
                e.workshopId().value(),
                e.oldStartTime(),
                e.oldEndTime(),
                e.newStartTime(),
                e.newEndTime(),
                e.occurredAt()
        );
    }
}
