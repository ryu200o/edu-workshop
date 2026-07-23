package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishEvents(List<?> domainEvents) {
        domainEvents.forEach(eventPublisher::publishEvent);
    }
}
