package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Application-layer helper (ADR 0005) that kicks {@code PLANNED} workshops back to
 * {@code DRAFT} when a workshop formally establishes exclusive ownership ({@code PUBLISHED})
 * over a room's time window. Used by {@link PublishWorkshopCommandHandler},
 * {@link ChangeWorkshopRoomCommandHandler} and {@link RescheduleWorkshopCommandHandler}:
 * overlapping {@code PLANNED} workshops are planning-only (ADR 0008) and must not keep
 * believing they hold the room.
 *
 * <p>ADR 0015 (lock-set-first): the set of overlapping workshops is loaded <em>and pessimistic-
 * locked</em> by the calling handler via {@code loadPublishedAndPlannedOverlappingWithLock} and
 * passed in as {@code toKick}; this helper only performs the state transition and the batch save.
 * </p>
 */
@Component
class PlannedWorkshopKicker {

    private final WorkshopRepository workshopRepository;

    PlannedWorkshopKicker(WorkshopRepository workshopRepository) {
        this.workshopRepository = workshopRepository;
    }

    /**
     * Evicts the already-locked {@code PLANNED} workshops back to {@code DRAFT} and persists them
     * in a single batch save.
     *
     * @param toKick the overlapping {@code PLANNED} workshops (already pessimistic-locked by the
     *               caller via {@code loadPublishedAndPlannedOverlappingWithLock}), never including
     *               the target itself
     * @param now    the current instant, forwarded to {@code evictPlanningOnConflict}
     * @return the list of workshops that were kicked back to {@code DRAFT}
     */
    List<Workshop> kickOutOverlappingPlanned(List<Workshop> toKick, Instant now) {
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
