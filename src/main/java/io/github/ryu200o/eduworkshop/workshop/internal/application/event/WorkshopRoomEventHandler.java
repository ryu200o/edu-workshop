package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomCapacityChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomMaintenanceScheduledIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRelocatedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRenamedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateContract;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class WorkshopRoomEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopRoomEventHandler.class);

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final Clock clock;

    WorkshopRoomEventHandler(WorkshopRepository workshopRepository,
                             WorkshopDomainEventPublisher workshopDomainEventPublisher,
                             Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
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
                case RoomMaintenanceScheduledIntegrationEvent e -> handleMaintenanceScheduled(e);
            }
        } catch (Exception ex) {
            log.error("Failed to handle Room integration event: {}", event, ex);
        }
    }

    /**
     * Auto-flags every PUBLISHED workshop whose time window overlaps the maintenance window with an
     * eviction notice (Titik 2). The workshop's state is deliberately NOT changed —
     * {@code Workshop.markRoomEvicted} only sets {@code isRoomEvicted = true} + {@code roomEvictedAt}
     * (a notice, not a cancellation). Overlap condition:
     * {@code w.startTime < maintEnd && w.endTime > maintStart}; a null {@code endTime} (indefinite
     * maintenance) matches every workshop starting after {@code startTime}.
     *
     * <p>Follows the 3-Phase Execution Pattern: (1) mutate domain + collect events, (2) batch persist
     * via {@code saveAll}, (3) batch publish domain events. Early-returns when no PUBLISHED workshop
     * overlaps.</p>
     */
    private void handleMaintenanceScheduled(RoomMaintenanceScheduledIntegrationEvent event) {
        Instant now = Instant.now(clock);
        List<Workshop> affected = workshopRepository.loadPublishedOverlappingWithTimeWindow(
                event.roomId(), event.startTime(), event.endTime());

        if (affected.isEmpty()) {
            log.debug("No PUBLISHED workshop overlaps maintenance window for room {} — nothing to flag",
                    event.roomId());
            return;
        }

        log.info("Room {} maintenance scheduled {} — flagging {} overlapping PUBLISHED workshop(s)",
                event.roomId(), event.startTime(), affected.size());

        // 1. Domain State Mutation & Event Collection
        List<WorkshopDomainEvent> allDomainEvents = new ArrayList<>();
        for (Workshop workshop : affected) {
            workshop.markRoomEvicted(now);
            allDomainEvents.addAll(workshop.recordedEvents());
            workshop.clearDomainEvents();
        }

        // 2. Batch Persistence (JDBC batching)
        workshopRepository.saveAll(affected);

        // 3. Batch Event Publication
        workshopDomainEventPublisher.publish(allDomainEvents);
    }

    private void handleRenamed(RoomRenamedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);

        // 1. Domain State Mutation & Collection (only workshops that actually changed)
        List<Workshop> changed = new ArrayList<>();
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    event.newName(),
                    ref.roomLocationSnapshot(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            changed.add(w);
        }

        if (changed.isEmpty()) {
            return;
        }

        // 2. Batch Persistence (JDBC batching)
        workshopRepository.saveAll(changed);
    }

    private void handleRelocated(RoomRelocatedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);

        // 1. Domain State Mutation & Collection (only workshops that actually changed)
        List<Workshop> changed = new ArrayList<>();
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    event.newLocation(),
                    ref.roomCapacitySnapshot());
            w.updateRoomSnapshot(updated, now);
            changed.add(w);
        }

        if (changed.isEmpty()) {
            return;
        }

        // 2. Batch Persistence (JDBC batching)
        workshopRepository.saveAll(changed);
    }

    private void handleCapacityChanged(RoomCapacityChangedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);

        // 1. Domain State Mutation & Collection (only workshops that actually changed)
        List<Workshop> changed = new ArrayList<>();
        for (Workshop w : workshops) {
            RoomReference ref = w.roomReference();
            if (ref == null) continue;
            RoomReference updated = RoomReference.of(
                    ref.roomId(),
                    ref.roomNameSnapshot(),
                    ref.roomLocationSnapshot(),
                    event.newCapacity());
            w.updateRoomSnapshot(updated, now);
            changed.add(w);
        }

        if (changed.isEmpty()) {
            return;
        }

        // 2. Batch Persistence (JDBC batching)
        workshopRepository.saveAll(changed);
    }

    private void handleStateChanged(RoomStateChangedIntegrationEvent event) {
        List<Workshop> workshops = workshopRepository.loadByRoomId(event.roomId());
        Instant now = Instant.now(clock);

        // 1. Domain State Mutation & Event Collection (only workshops that actually changed)
        List<Workshop> changed = new ArrayList<>();
        List<WorkshopDomainEvent> allDomainEvents = new ArrayList<>();
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
            changed.add(w);
            allDomainEvents.addAll(w.recordedEvents());
            w.clearDomainEvents();
        }

        if (changed.isEmpty()) {
            return;
        }

        // 2. Batch Persistence (JDBC batching)
        workshopRepository.saveAll(changed);

        // 3. Batch Event Publication
        if (!allDomainEvents.isEmpty()) {
            workshopDomainEventPublisher.publish(allDomainEvents);
        }
    }
}
