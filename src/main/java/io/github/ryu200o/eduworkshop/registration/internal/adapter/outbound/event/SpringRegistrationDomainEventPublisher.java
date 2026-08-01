package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationDomainEventPublisher;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges Registration domain events into Spring's {@link ApplicationEventPublisher}. Published
 * inside the business transaction, each event is captured by the Spring Modulith Event Publication
 * Registry (transactional outbox) for durable delivery.
 */
@Component
class SpringRegistrationDomainEventPublisher implements RegistrationDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringRegistrationDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<RegistrationDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
