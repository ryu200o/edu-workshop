package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.parameter.WorkshopBufferParameters;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
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

/**
 * Reschedules a PUBLISHED workshop to a new time window (ADR 0008: post-publish change, room and
 * registrations kept).
 *
 * <p>Orchestration (ADR 0005 + ADR 0015 lock-set-first): discovery read → pessimistic-lock the
 * whole overlapping set (PUBLISHED + PLANNED) in the new window → hard-block if another PUBLISHED
 * workshop occupies the new window → {@code Workshop.reschedule} (deadline 24h + window validity are
 * local invariants) → evict overlapping PLANNED workshops → save → publish all events via the outbox.
 * The new Occupancy Window start is the config pure function (ADR 0018: {@code occupancyStart =
 * newStartTime − currentConfigBuffer}).</p>
 */
@Component
class RescheduleWorkshopCommandHandler
        implements CommandHandler<RescheduleWorkshopCommand> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final WorkshopBufferParameters bufferParameters;
    private final Clock clock;

    RescheduleWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     PlannedWorkshopKicker plannedWorkshopKicker,
                                     WorkshopBufferParameters bufferParameters,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.plannedWorkshopKicker = plannedWorkshopKicker;
        this.bufferParameters = bufferParameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(RescheduleWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        // Discovery read (non-locking) to learn the target's room before locking.
        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        UUID roomId = workshop.roomReference().roomId();

        // Config pure function (ADR 0018): new Occupancy Window start = newStartTime − currentConfigBuffer.
        Instant newOccupancyStart = command.newStartTime()
                .minus(Duration.ofMinutes(bufferParameters.beforeDefaultMinutes()));

        // Lock-set-first (ADR 0015): pessimistic-lock ALL overlapping workshops (PUBLISHED +
        // PLANNED) in the NEW Occupancy Window. The target overlaps its own new window only when
        // the new window is not disjoint from its current one; otherwise it is resolved separately
        // below. The overlap is decided natively on the denormalized occupancy_start (native SQL
        // predicate, approved by the composite index idx_workshops_room_occupancy) — no widened
        // superset, no in-memory filter.
        List<Workshop> overlapping = workshopRepository.loadPublishedAndPlannedOverlappingWithLock(
                roomId, newOccupancyStart, command.newEndTime());

        Workshop target = overlapping.stream()
                .filter(w -> w.id().equals(workshopId))
                .findFirst()
                .orElseGet(() -> workshopRepository.loadByIdWithLock(workshopId)
                        .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId())));

        boolean publishedConflict = overlapping.stream()
                .anyMatch(w -> w.state() == WorkshopState.PUBLISHED && !w.id().equals(workshopId));
        if (publishedConflict) {
            throw new RoomConflictException(roomId, command.workshopId());
        }

        target.reschedule(command.newStartTime(), command.newEndTime(), newOccupancyStart, now);

        List<Workshop> plannedToKick = overlapping.stream()
                .filter(w -> w.state() == WorkshopState.PLANNED && !w.id().equals(workshopId))
                .toList();
        List<Workshop> kickedOut = plannedWorkshopKicker.kickOutOverlappingPlanned(plannedToKick, now);

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
