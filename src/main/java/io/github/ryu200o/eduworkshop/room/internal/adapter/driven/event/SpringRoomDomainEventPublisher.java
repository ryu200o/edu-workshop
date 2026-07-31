package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.event;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomDomainEventPublisher;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SpringRoomDomainEventPublisher
        implements RoomDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringRoomDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(List<RoomDomainEvent> events) {
        events.forEach(eventPublisher::publishEvent);
    }
}
