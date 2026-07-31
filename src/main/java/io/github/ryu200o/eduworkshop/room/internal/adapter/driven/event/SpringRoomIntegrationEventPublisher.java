package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomIntegrationEventPublisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringRoomIntegrationEventPublisher
        implements RoomIntegrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    SpringRoomIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(RoomIntegrationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
