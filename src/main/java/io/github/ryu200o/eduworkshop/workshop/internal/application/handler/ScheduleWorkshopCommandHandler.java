package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPlanningException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command.ScheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class ScheduleWorkshopCommandHandler
        implements CommandHandler<ScheduleWorkshopCommand, ScheduleWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final RoomExposeAPI roomExposeApi;
    private final Clock clock;

    ScheduleWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                   RoomExposeAPI roomExposeApi,
                                   Clock clock) {
        this.workshopRepository = workshopRepository;
        this.roomExposeApi = roomExposeApi;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ScheduleWorkshopCommand.Result handle(ScheduleWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.checkPlanningPermission(command.roomId())
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", command.roomId()));

        if (permission.status() == RoomPlanningPermission.PlanningStatus.BLOCKED) {
            throw new RoomNotAvailableForPlanningException(command.roomId(), permission.reason());
        }

        boolean hasRoomWarning = permission.status() == RoomPlanningPermission.PlanningStatus.WARNING;

        String locationSnapshot = permission.planning().location().building()
                + "/" + permission.planning().location().floor();
        RoomReference roomRef = RoomReference.of(
                permission.planning().roomId(),
                permission.planning().roomName(),
                locationSnapshot,
                permission.planning().capacity());

        workshop.schedule(roomRef, hasRoomWarning, now);

        workshopRepository.save(workshop);

        return new ScheduleWorkshopCommand.Result(
                workshop.id().value(),
                workshop.roomReference().roomId(),
                workshop.updatedAt(),
                workshop.hasRoomWarning());
    }
}
