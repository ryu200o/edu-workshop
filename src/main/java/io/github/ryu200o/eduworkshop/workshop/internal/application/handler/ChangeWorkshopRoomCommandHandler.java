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
    private final Clock clock;

    ChangeWorkshopRoomCommandHandler(WorkshopRepository workshopRepository,
                                     RoomExposeAPI roomExposeApi,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.roomExposeApi = roomExposeApi;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChangeWorkshopRoomCommand.Result handle(ChangeWorkshopRoomCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());
        UUID newRoomId = command.newRoomId();

        Workshop workshop = workshopRepository.loadByIdWithLock(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.checkPlanningPermission(newRoomId)
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", newRoomId));

        if (permission.status() != RoomPlanningPermission.PlanningStatus.ALLOWED) {
            throw new RoomNotAvailableForPublishingException(newRoomId, permission.status(), permission.reason());
        }

        int overlappingPublished = workshopRepository.countOverlapping(
                newRoomId, workshop.startTime(), workshop.endTime(), workshopId);
        if (overlappingPublished > 0) {
            throw new RoomConflictException(newRoomId, command.workshopId());
        }

        List<Workshop> kickedOut = kickOutOverlappingScheduled(newRoomId, workshop, now);

        RoomReference newRoomRef = RoomReference.of(
                permission.planning().roomId(),
                permission.planning().roomName(),
                permission.planning().location().building() + "/" + permission.planning().location().floor(),
                permission.planning().capacity());

        workshop.changeRoom(newRoomRef, now);
        workshopRepository.save(workshop);

        List<WorkshopDomainEvent> events = new ArrayList<>(workshop.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        workshop.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);

        return new ChangeWorkshopRoomCommand.Result(workshop.id().value(), newRoomRef.roomId(), workshop.updatedAt());
    }

    private List<Workshop> kickOutOverlappingScheduled(UUID newRoomId, Workshop target, Instant now) {
        List<Workshop> kickedOut = new ArrayList<>();
        for (Workshop other : workshopRepository.loadByRoomId(newRoomId)) {
            if (other.id().equals(target.id())) {
                continue;
            }
            if (other.state() != WorkshopState.SCHEDULED) {
                continue;
            }
            if (!overlaps(other, target)) {
                continue;
            }
            other.returnToDraft(now);
            workshopRepository.save(other);
            kickedOut.add(other);
        }
        return kickedOut;
    }

    private boolean overlaps(Workshop other, Workshop target) {
        return other.startTime().isBefore(target.endTime())
                && target.startTime().isBefore(other.endTime());
    }
}
