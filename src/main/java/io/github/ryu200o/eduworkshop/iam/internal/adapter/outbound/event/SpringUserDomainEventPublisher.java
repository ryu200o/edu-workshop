package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes the IAM domain events recorded by the {@code User} aggregate to the Spring application
 * context (mirrors the Room module's {@code SpringRoomDomainEventPublisher}). Integration events
 * (outbox, ADR 0011) are a later slice concern.
 */
@Component
class SpringUserDomainEventPublisher implements UserDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringUserDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<UserDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
