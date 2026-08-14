package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceDomainEventPublisher;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.event.AttendanceDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges Attendance domain events into Spring's {@link ApplicationEventPublisher}. Published inside
 * the business transaction, each event is captured by the Spring Modulith Event Publication Registry
 * (transactional outbox) for durable delivery.
 */
@Component
class SpringAttendanceDomainEventPublisher implements AttendanceDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringAttendanceDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<AttendanceDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}