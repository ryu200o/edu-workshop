package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.iam.contract.events.UserIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserIntegrationEventPublisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bridges IAM integration events into Spring's {@link ApplicationEventPublisher}. Published inside
 * the business transaction, each event is captured by the Spring Modulith Event Publication Registry
 * (transactional outbox) for durable delivery (ADR 0011).
 */
@Component
class SpringUserIntegrationEventPublisher implements UserIntegrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringUserIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(UserIntegrationEvent event) {
        eventPublisher.publishEvent(event);
    }
}