package io.github.ryu200o.eduworkshop.registration.internal.application.port.out;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.event.RegistrationDomainEvent;

import java.util.List;

/**
 * Outbound port for publishing Registration domain events. Implemented by an adapter that bridges
 * domain events into Spring's {@link org.springframework.context.ApplicationEventPublisher} — the
 * Spring Modulith Event Publication Registry (transactional outbox) then persists and delivers them.
 */
public interface RegistrationEventPublisher {

    void publish(List<RegistrationDomainEvent> events);
}
