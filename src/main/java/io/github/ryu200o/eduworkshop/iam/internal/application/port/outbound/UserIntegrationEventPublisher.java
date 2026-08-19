package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.contract.events.UserIntegrationEvent;

/**
 * Outbound port for publishing cross-module integration events emitted by the IAM module.
 * Implemented by an outbound adapter that bridges integration events into Spring's
 * {@link org.springframework.context.ApplicationEventPublisher} — the Spring Modulith Event
 * Publication Registry (transactional outbox) then persists and delivers them (ADR 0011).
 */
public interface UserIntegrationEventPublisher {

    void publish(UserIntegrationEvent event);
}