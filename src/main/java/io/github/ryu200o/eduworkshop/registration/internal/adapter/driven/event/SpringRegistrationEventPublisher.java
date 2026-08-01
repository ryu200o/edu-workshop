package io.github.ryu200o.eduworkshop.registration.internal.adapter.driven.event;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.out.RegistrationEventPublisher;
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
class SpringRegistrationEventPublisher implements RegistrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringRegistrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<RegistrationDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
