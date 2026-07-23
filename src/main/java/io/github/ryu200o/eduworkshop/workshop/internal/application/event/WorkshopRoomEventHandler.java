package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class WorkshopRoomEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopRoomEventHandler.class);

    private final WorkshopRepository workshopRepository;
    private final Clock clock;

    WorkshopRoomEventHandler(WorkshopRepository workshopRepository, Clock clock) {
        this.workshopRepository = workshopRepository;
        this.clock = clock;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleRoomEvent(RoomDomainEvent event) {
        try {
            switch (event) {
                case RoomRenamedEvent e -> handleRenamed(e);
                case RoomRelocatedEvent e -> handleRelocated(e);
                case RoomStateChanged e -> handleStateChanged(e);
                case RoomCapacityChanged e -> handleCapacityChanged(e);
                case RoomCreated e -> { /* no-op: workshop can't reference a room before it exists */ }
            }
        } catch (Exception ex) {
            log.error("Failed to handle Room event: {}", event, ex);
        }
    }

    private void handleRenamed(RoomRenamedEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId().value());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    event.newName().value(),
                    ref.roomLocationSnapshot(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleRelocated(RoomRelocatedEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId().value());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    event.newLocation().asString(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleCapacityChanged(RoomCapacityChanged event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId().value());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    ref.roomLocationSnapshot(),
                    event.newCapacity().value());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleStateChanged(RoomStateChanged event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId().value());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            if (w.roomReference() == null) continue;
            switch (event.newState()) {
                case MAINTENANCE -> w.markMaintenanceWarning(now);
                case ACTIVE -> {
                    if (event.previousState() == RoomState.MAINTENANCE) {
                        w.clearMaintenanceWarning(now);
                    }
                }
                case DEACTIVATED -> w.returnToDraft(now);
            }
            workshopRepository.save(w);
        }
    }
}
