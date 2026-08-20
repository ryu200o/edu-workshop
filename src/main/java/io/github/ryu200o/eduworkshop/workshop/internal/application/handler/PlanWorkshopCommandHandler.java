package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomNotAvailableForPlanningException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PlanWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.RoomReference;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
class PlanWorkshopCommandHandler
        implements CommandHandler<PlanWorkshopCommand> {

    private final WorkshopRepository workshopRepository;
    private final RoomExposeAPI roomExposeApi;
    private final WorkshopBufferParameters bufferParameters;
    private final Clock clock;

    PlanWorkshopCommandHandler(WorkshopRepository workshopRepository,
                               RoomExposeAPI roomExposeApi,
                               WorkshopBufferParameters bufferParameters,
                               Clock clock) {
        this.workshopRepository = workshopRepository;
        this.roomExposeApi = roomExposeApi;
        this.bufferParameters = bufferParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(PlanWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        RoomPlanningPermission permission = roomExposeApi.getPlanningPermission(command.roomId())
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

        // Config pure function (ADR 0018): room-space assignment is a scheduling-axis mutation, so
        // the Occupancy Window start is re-applied against the current Ops buffer (occupancyStart =
        // startTime − currentConfigBuffer).
        Instant occupancyStart = workshop.startTime()
                .minus(Duration.ofMinutes(bufferParameters.beforeDefaultMinutes()));

        workshop.plan(roomRef, hasRoomWarning, occupancyStart, now);

        workshopRepository.save(workshop);
    }
}
