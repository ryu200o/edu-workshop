package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPublishingException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PublishWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
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
class PublishWorkshopCommandHandler
        implements CommandHandler<PublishWorkshopCommand, PublishWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final RoomExposeAPI roomExposeApi;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final Clock clock;

    PublishWorkshopCommandHandler(WorkshopRepository workshopRepository,
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
    public PublishWorkshopCommand.Result handle(PublishWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        // Discovery read (non-locking) to learn the target's room + time window before locking.
        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        UUID roomId = workshop.roomReference().roomId();

        RoomPlanningPermission permission = roomExposeApi.findPlanningPermission(roomId)
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", roomId));

        if (permission.status() != RoomPlanningPermission.PlanningStatus.ALLOWED) {
            throw new RoomNotAvailableForPublishingException(roomId, permission.status(), permission.reason());
        }

        // Lock-set-first (ADR 0015): pessimistic-lock ALL overlapping workshops (PUBLISHED +
        // PLANNED) in the target room/window before mutating any state. The target overlaps its
        // own window, so it is covered by the same lock — resolving it from the list reuses that
        // locked, fresh instance.
        List<Workshop> overlapping = workshopRepository.loadPublishedAndPlannedOverlappingWithLock(
                roomId, workshop.startTime(), workshop.endTime());

        Workshop target = overlapping.stream()
                .filter(w -> w.id().equals(workshopId))
                .findFirst()
                .orElseGet(() -> workshopRepository.loadByIdWithLock(workshopId)
                        .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId())));

        if (target.hasRoomWarning()) {
            target.clearMaintenanceWarning(now);
        }

        boolean publishedConflict = overlapping.stream()
                .anyMatch(w -> w.state() == WorkshopState.PUBLISHED && !w.id().equals(workshopId));
        if (publishedConflict) {
            throw new RoomConflictException(roomId, command.workshopId());
        }

        List<Workshop> plannedToKick = overlapping.stream()
                .filter(w -> w.state() == WorkshopState.PLANNED && !w.id().equals(workshopId))
                .toList();
        List<Workshop> kickedOut = plannedWorkshopKicker.kickOutOverlappingPlanned(plannedToKick, now);

        target.publish(now, permission.planning().capacity());

        workshopRepository.save(target);

        List<WorkshopDomainEvent> events = new ArrayList<>(target.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        target.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);

        return new PublishWorkshopCommand.Result(target.id().value(), target.updatedAt());
    }
}
