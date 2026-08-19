package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPublishingException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.ChangeWorkshopRoomCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class ChangeWorkshopRoomCommandHandler
        implements CommandHandler<ChangeWorkshopRoomCommand> {

    private final WorkshopRepository workshopRepository;
    private final RoomExposeAPI roomExposeApi;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final WorkshopBufferParameters bufferParameters;
    private final Clock clock;

    ChangeWorkshopRoomCommandHandler(WorkshopRepository workshopRepository,
                                     RoomExposeAPI roomExposeApi,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     PlannedWorkshopKicker plannedWorkshopKicker,
                                     WorkshopBufferParameters bufferParameters,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.roomExposeApi = roomExposeApi;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.plannedWorkshopKicker = plannedWorkshopKicker;
        this.bufferParameters = bufferParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(ChangeWorkshopRoomCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());
        UUID newRoomId = command.newRoomId();

        // Discovery read (non-locking) to learn the target's time window before locking.
        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.getPlanningPermission(newRoomId)
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", newRoomId));

        if (permission.status() != RoomPlanningPermission.PlanningStatus.ALLOWED) {
            throw new RoomNotAvailableForPublishingException(newRoomId, permission.status(), permission.reason());
        }

        // Lock-set-first (ADR 0015): pessimistic-lock ALL overlapping workshops (PUBLISHED +
        // PLANNED) in the NEW room/time window before mutating any state.
        // Config pure function (ADR 0018): changing the room is a room-space scheduling-axis
        // mutation, so the Occupancy Window start is re-applied against the current Ops buffer
        // (occupancyStart = startTime − currentConfigBuffer).
        Instant newOccupancyStart = workshop.startTime()
                .minus(Duration.ofMinutes(bufferParameters.beforeDefaultMinutes()));
        Instant occEnd = workshop.endTime();
        // The overlap is decided natively on the denormalized Occupancy Window (ADR 0018): the SQL
        // predicate compares occupancy_start/end_time (approved by the composite index
        // idx_workshops_room_occupancy) — no widened superset, no in-memory filter.
        List<Workshop> overlapping = workshopRepository.loadPublishedAndPlannedOverlappingWithLock(
                newRoomId, newOccupancyStart, occEnd);

        // The target lives in the OLD room, so it is never part of the new-room set — lock it
        // separately (after the set, preserving the consistent set-first lock order).
        Workshop target = workshopRepository.loadByIdWithLock(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        boolean publishedConflict = overlapping.stream()
                .anyMatch(w -> w.state() == WorkshopState.PUBLISHED);
        if (publishedConflict) {
            throw new RoomConflictException(newRoomId, command.workshopId());
        }

        List<Workshop> plannedToKick = overlapping.stream()
                .filter(w -> w.state() == WorkshopState.PLANNED)
                .toList();
        List<Workshop> kickedOut = plannedWorkshopKicker.kickOutOverlappingPlanned(plannedToKick, now);

        RoomReference newRoomRef = RoomReference.of(
                permission.planning().roomId(),
                permission.planning().roomName(),
                permission.planning().location().building() + "/" + permission.planning().location().floor(),
                permission.planning().capacity());

        target.changeRoom(newRoomRef, newOccupancyStart, now);
        workshopRepository.save(target);

        List<WorkshopDomainEvent> events = new ArrayList<>(target.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        target.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);
    }
}
