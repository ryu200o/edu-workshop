package io.github.ryu200o.eduworkshop.room.contract;

import java.time.Instant;
import java.util.UUID;

public sealed interface RoomIntegrationEvent
        permits RoomRenamedIntegrationEvent,
                RoomRelocatedIntegrationEvent,
                RoomCapacityChangedIntegrationEvent,
                RoomStateChangedIntegrationEvent,
                RoomMaintenanceScheduledIntegrationEvent {

    UUID roomId();

    Instant occurredAt();
}
