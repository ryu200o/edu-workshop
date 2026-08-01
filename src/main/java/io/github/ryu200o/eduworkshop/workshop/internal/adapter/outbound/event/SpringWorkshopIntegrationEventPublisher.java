package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.workshop.contract.events.WorkshopIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopIntegrationEventPublisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bridges Workshop integration events into Spring's {@link ApplicationEventPublisher}. Published
 * inside the business transaction, each event is captured by the Spring Modulith Event Publication
 * Registry (transactional outbox) for durable delivery.
 */
@Component
class SpringWorkshopIntegrationEventPublisher implements WorkshopIntegrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringWorkshopIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(WorkshopIntegrationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
