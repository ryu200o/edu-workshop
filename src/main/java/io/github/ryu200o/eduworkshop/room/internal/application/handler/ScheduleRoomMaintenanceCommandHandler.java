package io.github.ryu200o.eduworkshop.room.internal.application.handler;

import io.github.ryu200o.eduworkshop.room.internal.application.exception.MaintenanceScheduleOverlapException;
import io.github.ryu200o.eduworkshop.room.internal.application.exception.RoomNotFoundException;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.ScheduleRoomMaintenanceCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.MaintenanceScheduleRepository;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomDomainEventPublisher;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Handler for {@link ScheduleRoomMaintenanceCommand}. Follows the standard Application-layer
 * pattern (ADR 0005) plus the pessimistic-write-lock strategy (ADR 0015 Technique 1): loads the
 * aggregate under a {@code SELECT ... FOR UPDATE} lock so the cross-record overlap invariant is
 * validated against a consistent snapshot, then delegates to the aggregate for local invariants,
 * persists and publishes events.
 */
@Component
class ScheduleRoomMaintenanceCommandHandler
        implements CommandHandler<ScheduleRoomMaintenanceCommand, ScheduleRoomMaintenanceCommand.Result> {

    private final RoomRepository roomRepository;
    private final MaintenanceScheduleRepository maintenanceScheduleRepository;
    private final RoomDomainEventPublisher roomDomainEventPublisher;
    private final Clock clock;

    ScheduleRoomMaintenanceCommandHandler(RoomRepository roomRepository,
                                           MaintenanceScheduleRepository maintenanceScheduleRepository,
                                           RoomDomainEventPublisher roomDomainEventPublisher,
                                           Clock clock) {
        this.roomRepository = roomRepository;
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
        this.roomDomainEventPublisher = roomDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ScheduleRoomMaintenanceCommand.Result handle(ScheduleRoomMaintenanceCommand command) {
        Instant now = Instant.now(clock);
        RoomId roomId = RoomId.of(command.roomId());

        // ADR 0015 Technique 1: pessimistic write lock so the overlap check sees a consistent snapshot.
        Room room = roomRepository.loadByIdWithLock(roomId)
                .orElseThrow(() -> new RoomNotFoundException("id", command.roomId()));

        // Global invariant (ADR 0005): check for overlapping maintenance schedules.
        List<MaintenanceSchedule> overlapping = maintenanceScheduleRepository.loadOverlapping(
                command.roomId(), command.startTime(), command.endTime());
        if (!overlapping.isEmpty()) {
            throw new MaintenanceScheduleOverlapException(
                    command.roomId(), command.startTime(), command.endTime());
        }

        // Delegate to aggregate for local invariants
        MaintenanceId maintenanceId = MaintenanceId.generate();
        MaintenanceSchedule schedule = room.scheduleMaintenance(
                maintenanceId, command.startTime(), command.endTime(),
                command.reason(), command.operator(), now);

        // Persist
        maintenanceScheduleRepository.save(schedule);

        // Publish domain events
        roomDomainEventPublisher.publish(room.recordedEvents());
        room.clearDomainEvents();

        return new ScheduleRoomMaintenanceCommand.Result(
                schedule.id().value(),
                room.id().value(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.createdAt());
    }
}