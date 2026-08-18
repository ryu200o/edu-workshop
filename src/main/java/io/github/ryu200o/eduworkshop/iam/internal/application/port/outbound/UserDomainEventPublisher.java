package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserDomainEvent;

import java.util.List;

/**
 * Outbound port (SPI) for publishing the domain events recorded by the {@code User} aggregate to the
 * Spring application context (mirrors the Room module's {@code RoomDomainEventPublisher}). Integration
 * events (outbox, ADR 0011) are a separate concern and arrive in a later slice.
 */
public interface UserDomainEventPublisher {

    void publish(List<UserDomainEvent> events);
}
