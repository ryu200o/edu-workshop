package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPublishingException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.ChangeWorkshopRoomCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.AdjustmentJustification;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class ChangeWorkshopRoomCommandHandler
        implements CommandHandler<ChangeWorkshopRoomCommand, ChangeWorkshopRoomCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final RoomExposeAPI roomExposeApi;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final Clock clock;

    ChangeWorkshopRoomCommandHandler(WorkshopRepository workshopRepository,
                                     RoomExposeAPI roomExposeApi,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     PlannedWorkshopKicker plannedWorkshopKicker,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.roomExposeApi = roomExposeApi;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.plannedWorkshopKicker = plannedWorkshopKicker;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChangeWorkshopRoomCommand.Result handle(ChangeWorkshopRoomCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());
        UUID newRoomId = command.newRoomId();
        AdjustmentJustification justification = AdjustmentJustification.of(command.justification());

        // Discovery read (non-locking) to learn the target's time window before locking.
        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.getPlanningPermission(newRoomId)
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", newRoomId));

        if (permission.status() != RoomPlanningPermission.PlanningStatus.ALLOWED) {
            throw new RoomNotAvailableForPublishingException(newRoomId, permission.status(), permission.reason());
        }

        // Lock-set-first (ADR 0015): pessimistic-lock ALL overlapping workshops (PUBLISHED +
        // PLANNED) in the NEW room's scheduled-occupancy window (Spec v2 / ADR 0018) before
        // mutating any state.
        List<Workshop> overlapping = workshopRepository.loadPublishedAndPlannedOverlappingWithLock(
                newRoomId, workshop.occupancyStart(), workshop.occupancyEnd());

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

        target.changeRoom(newRoomRef, now);
        workshopRepository.save(target);

        List<WorkshopDomainEvent> events = new ArrayList<>(target.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        target.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);

        return new ChangeWorkshopRoomCommand.Result(target.id().value(), newRoomRef.roomId(), target.updatedAt());
    }
}
