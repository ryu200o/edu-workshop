package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopIntegrationEvent;

/**
 * Outbound port for publishing cross-module integration events emitted by the Workshop module.
 * Implemented by an outbound adapter that bridges integration events into Spring's
 * {@link org.springframework.context.ApplicationEventPublisher} — the Spring Modulith Event
 * Publication Registry (transactional outbox) then persists and delivers them (ADR 0011).
 */
public interface WorkshopIntegrationEventPublisher {

    void publish(WorkshopIntegrationEvent event);
}
