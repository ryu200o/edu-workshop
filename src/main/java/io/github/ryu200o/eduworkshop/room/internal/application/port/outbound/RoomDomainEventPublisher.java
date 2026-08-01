package io.github.ryu200o.eduworkshop.room.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;

import java.util.List;

public interface RoomDomainEventPublisher {

    void publish(List<RoomDomainEvent> events);
}
