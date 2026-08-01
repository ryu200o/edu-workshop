package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import java.util.List;

public interface WorkshopDomainEventPublisher {

    void publish(List<WorkshopDomainEvent> events);
}
