package io.github.ryu200o.eduworkshop.workshop;

import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopImpactContract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public inter-module communication interface for the workshop module.
 * This is the only surface exposed to other modules.
 */
public interface WorkshopExposeAPI {

    /**
     * Acquires a pessimistic write lock on the workshop row (lock-anchor, ADR 0015) and returns the
     * registration snapshot. Used by the Registration module's capacity gate: all concurrent
     * registrations for the same workshop serialize on this single row-lock (the {@code workshops}
     * row always exists, unlike a possibly-empty {@code registrations} set), so the subsequent
     * {@code countActiveByWorkshop} read is stable and no seat is over-booked. Empty when the
     * workshop does not exist.
     */
    Optional<WorkshopRegistrationContract> lockForRegistration(UUID workshopId);

    /**
     * Returns the workshops assigned to a given room whose <em>scheduled occupancy window</em>
     * (Spec v2 / ADR 0018) overlaps the specified range, as consumer-driven DTOs (id + state).
     * Empty when no workshop overlaps. Signature unchanged — semantics now compare against the
     * scheduled occupancy window rather than the teaching window.
     *
     * @param roomId    the room to filter by
     * @param startTime the maintenance window start (inclusive lower bound)
     * @param endTime   the maintenance window end (null = indefinite)
     */
    List<WorkshopImpactContract> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime);
}
