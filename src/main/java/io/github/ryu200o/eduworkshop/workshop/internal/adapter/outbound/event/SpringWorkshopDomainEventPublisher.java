package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.event;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SpringWorkshopDomainEventPublisher
        implements WorkshopDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringWorkshopDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<WorkshopDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
