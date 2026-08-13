package io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;

import java.util.List;

/**
 * Outbound port for publishing Attendance domain events. Implemented by an adapter that bridges
 * domain events into Spring's {@link org.springframework.context.ApplicationEventPublisher} — the
 * Spring Modulith Event Publication Registry (transactional outbox) then persists and delivers them
 * (ADR 0011).
 */
public interface AttendanceDomainEventPublisher {

    void publish(List<AttendanceDomainEvent> events);
}