package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomCapacityChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRelocatedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRenamedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateContract;
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
    public void handleIntegrationEvent(RoomIntegrationEvent event) {
        try {
            switch (event) {
                case RoomRenamedIntegrationEvent e -> handleRenamed(e);
                case RoomRelocatedIntegrationEvent e -> handleRelocated(e);
                case RoomStateChangedIntegrationEvent e -> handleStateChanged(e);
                case RoomCapacityChangedIntegrationEvent e -> handleCapacityChanged(e);
            }
        } catch (Exception ex) {
            log.error("Failed to handle Room integration event: {}", event, ex);
        }
    }

    private void handleRenamed(RoomRenamedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    event.newName(),
                    ref.roomLocationSnapshot(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleRelocated(RoomRelocatedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    event.newLocation(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleCapacityChanged(RoomCapacityChangedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    ref.roomLocationSnapshot(),
                    event.newCapacity());
            w.updateRoomSnapshot(updated, now);
            workshopRepository.save(w);
        }
    }

    private void handleStateChanged(RoomStateChangedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);
        for (Workshop w : workshops) {
            if (w.roomReference() == null) continue;
            switch (event.newState()) {
                case MAINTENANCE -> w.markMaintenanceWarning(now);
                case ACTIVE -> {
                    if (event.previousState() == RoomStateContract.MAINTENANCE) {
                        w.clearMaintenanceWarning(now);
                    }
                }
                case DEACTIVATED -> w.returnToDraft(now);
            }
            workshopRepository.save(w);
        }
    }
}
