package io.github.ryu200o.eduworkshop.workshop.internal.application.port.out;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import java.util.List;

public interface WorkshopEventPublisher {

    void publish(List<WorkshopDomainEvent> events);
}
