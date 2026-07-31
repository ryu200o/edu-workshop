package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.contract.RoomIntegrationEvent;

public interface RoomIntegrationEventPublisher {
     void publish(RoomIntegrationEvent event);
}
