package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.event;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SpringWorkshopEventPublisher
        implements WorkshopEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringWorkshopEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<WorkshopDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
