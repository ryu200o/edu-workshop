package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer helper (ADR 0005) that kicks {@code PLANNED} workshops back to
 * {@code DRAFT} when a workshop formally establishes exclusive ownership ({@code PUBLISHED})
 * over a room's time window. Used by {@link PublishWorkshopCommandHandler},
 * {@link ChangeWorkshopRoomCommandHandler} and {@link RescheduleWorkshopCommandHandler}:
 * overlapping {@code PLANNED} workshops are planning-only (ADR 0008) and must not keep
 * believing they hold the room.
 *
 * <p>Optimized: uses {@link WorkshopRepository#loadOverlappingPlanned} to push the overlap
 * filter into SQL/JPQL (no full room scan, no in-memory overlap check), then batches the
 * state transitions and persists them in a single {@code saveAll} call.</p>
 */
@Component
class PlannedWorkshopKicker {

    private final WorkshopRepository workshopRepository;

    PlannedWorkshopKicker(WorkshopRepository workshopRepository) {
        this.workshopRepository = workshopRepository;
    }

    /**
     * Loads only the {@code PLANNED} workshops that truly overlap the target's time window
     * (pushed into SQL/JPQL), evicts them to {@code DRAFT}, and persists them in a single
     * batch save.
     *
     * @param roomId the room id the target is establishing ownership over
     * @param target the workshop that is being published / moved / rescheduled into the room
     *               (never kicked)
     * @param now    the current instant, forwarded to {@code evictPlanningOnConflict}
     * @return the list of workshops that were kicked back to {@code DRAFT}
     */
    List<Workshop> kickOutOverlappingPlanned(UUID roomId, Workshop target, Instant now) {
        List<Workshop> toKick = workshopRepository.loadOverlappingPlanned(
                roomId, target.startTime(), target.endTime(), target.id());

        if (toKick.isEmpty()) {
            return List.of();
        }

        for (Workshop other : toKick) {
            other.evictPlanningOnConflict(now);
        }

        workshopRepository.saveAll(toKick);
        return toKick;
    }
}
