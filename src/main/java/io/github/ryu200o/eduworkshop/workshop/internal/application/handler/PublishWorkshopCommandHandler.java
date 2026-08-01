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
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopDomainEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        Workshop workshop = workshopRepository.loadByIdWithLock(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.checkPlanningPermission(workshop.roomReference().roomId())
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", workshop.roomReference().roomId()));

        if (permission.status() != RoomPlanningPermission.PlanningStatus.ALLOWED) {
            throw new RoomNotAvailableForPublishingException(
                    workshop.roomReference().roomId(),
                    permission.status(),
                    permission.reason());
        }

        if (workshop.hasRoomWarning()) {
            workshop.clearMaintenanceWarning(now);
        }

        int overlapping = workshopRepository.countOverlapping(
                workshop.roomReference().roomId(),
                workshop.startTime(),
                workshop.endTime(),
                workshopId);

        if (overlapping > 0) {
            throw new RoomConflictException(workshop.roomReference().roomId(), command.workshopId());
        }

        List<Workshop> kickedOut = plannedWorkshopKicker.kickOutOverlappingPlanned(
                workshop.roomReference().roomId(), workshop, now);

        workshop.publish(now, permission.planning().capacity());

        workshopRepository.save(workshop);

        List<WorkshopDomainEvent> events = new ArrayList<>(workshop.recordedEvents());
        for (Workshop other : kickedOut) {
            events.addAll(other.recordedEvents());
        }

        workshopDomainEventPublisher.publish(events);
        workshop.clearDomainEvents();
        kickedOut.forEach(Workshop::clearDomainEvents);

        return new PublishWorkshopCommand.Result(workshop.id().value(), workshop.updatedAt());
    }
}
