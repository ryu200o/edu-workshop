package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.adapter.inbound.config.WorkshopBufferConfig;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.InvalidBufferSizeException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.RoomConflictException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.RescheduleWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopDomainEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.AdjustmentJustification;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopBuffer;
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

/**
 * Reschedules a PUBLISHED workshop to a new time window (ADR 0008: post-publish change, room and
 * registrations kept).
 *
 * <p>Orchestration (ADR 0005 + ADR 0015 lock-set-first): discovery read → pessimistic-lock the
 * whole overlapping set (PUBLISHED + PLANNED) in the new window → hard-block if another PUBLISHED
 * workshop occupies the new window → {@code Workshop.reschedule} (deadline 24h + window validity are
 * local invariants) → evict overlapping PLANNED workshops → save → publish all events via the outbox.</p>
 */
@Component
class RescheduleWorkshopCommandHandler
        implements CommandHandler<RescheduleWorkshopCommand, RescheduleWorkshopCommand.Result> {

    private final WorkshopRepository workshopRepository;
    private final WorkshopDomainEventPublisher workshopDomainEventPublisher;
    private final PlannedWorkshopKicker plannedWorkshopKicker;
    private final WorkshopBufferConfig workshopBufferConfig;
    private final Clock clock;

    RescheduleWorkshopCommandHandler(WorkshopRepository workshopRepository,
                                     WorkshopDomainEventPublisher workshopDomainEventPublisher,
                                     PlannedWorkshopKicker plannedWorkshopKicker,
                                     WorkshopBufferConfig workshopBufferConfig,
                                     Clock clock) {
        this.workshopRepository = workshopRepository;
        this.workshopDomainEventPublisher = workshopDomainEventPublisher;
        this.plannedWorkshopKicker = plannedWorkshopKicker;
        this.workshopBufferConfig = workshopBufferConfig;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RescheduleWorkshopCommand.Result handle(RescheduleWorkshopCommand command) {
        Instant now = Instant.now(clock);
        WorkshopId workshopId = WorkshopId.of(command.workshopId());

        // Validate contract terms up-front (ADR 0018 P4) — fail before any DB read.
        AdjustmentJustification justification = AdjustmentJustification.of(command.justification());
        WorkshopBuffer newBuffer = resolveNewBuffer(command.bufferBeforeMinutes(), command.bufferAfterMinutes());

        // Discovery read (non-locking) to learn the target's room before locking.
        Workshop workshop = workshopRepository.loadById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException("id", command.workshopId()));

        UUID roomId = workshop.roomReference().roomId();

        // Compute the target's prospective occupancy window (teaching + buffer) for the conflict check.
        Instant prospectiveOccupancyStart = command.newStartTime().minusSeconds(
                (newBuffer != null ? newBuffer.beforeMinutes() : workshop.buffer().beforeMinutes()) * 60L);
        Instant prospectiveOccupancyEnd = command.newEndTime().plusSeconds(
                (newBuffer != null ? newBuffer.afterMinutes() : workshop.buffer().afterMinutes()) * 60L);

        // Lock-set-first (ADR 0015): pessimistic-lock ALL overlapping workshops (PUBLISHED +
        // PLANNED) in the NEW scheduled-occupancy window.
        List<Workshop> overlapping = workshopRepository.loadPublishedAndPlannedOverlappingWithLock(
                roomId, prospectiveOccupancyStart, prospectiveOccupancyEnd);

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

        target.reschedule(command.newStartTime(), command.newEndTime(), justification, newBuffer, now);

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

        return new RescheduleWorkshopCommand.Result(
                target.id().value(),
                target.startTime(),
                target.endTime(),
                target.updatedAt());
    }

    private WorkshopBuffer resolveNewBuffer(Integer before, Integer after) {
        if (before == null && after == null) {
            return null;
        }
        int resolvedBefore = before != null ? before : workshopBufferConfig.beforeDefaultMinutes();
        int resolvedAfter = after != null ? after : workshopBufferConfig.afterDefaultMinutes();
        if (resolvedBefore < workshopBufferConfig.minMinutes() || resolvedBefore > workshopBufferConfig.maxMinutes()
                || resolvedAfter < workshopBufferConfig.minMinutes() || resolvedAfter > workshopBufferConfig.maxMinutes()) {
            throw new InvalidBufferSizeException(
                    "buffer before/after must be within [" + workshopBufferConfig.minMinutes()
                            + ", " + workshopBufferConfig.maxMinutes() + "] minutes");
        }
        return WorkshopBuffer.of(resolvedBefore, resolvedAfter);
    }
}
