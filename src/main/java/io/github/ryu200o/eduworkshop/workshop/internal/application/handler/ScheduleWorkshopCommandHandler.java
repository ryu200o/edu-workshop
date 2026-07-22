package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomSnapshot;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.ReferencedRoomNotFoundException;
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

/**
 * Handler for {@link ScheduleWorkshopCommand}. Orchestrates the DRAFT → SCHEDULED transition:
 * loads the workshop, fetches room snapshot via {@link RoomExposeAPI}, builds a
 * {@link RoomReference}, delegates to the domain aggregate, and persists.
 *
 * <p>Per ADR 0010, cross-module data access stays entirely in the Application layer —
 * the handler maps the Room contract DTO into Workshop's domain VO.</p>
 */
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

        RoomSnapshot snapshot = roomExposeApi.findRoomSnapshot(command.roomId())
                .orElseThrow(() -> new ReferencedRoomNotFoundException("roomId", command.roomId()));

        String locationSnapshot = snapshot.location().building() + "/" + snapshot.location().floor();
        RoomReference roomRef = RoomReference.of(snapshot.roomId(), snapshot.name(), locationSnapshot);

        workshop.schedule(roomRef, now);

        workshopRepository.save(workshop);

        return new ScheduleWorkshopCommand.Result(
                workshop.id().value(),
                workshop.roomReference().roomId(),
                workshop.updatedAt()
        );
    }
}
