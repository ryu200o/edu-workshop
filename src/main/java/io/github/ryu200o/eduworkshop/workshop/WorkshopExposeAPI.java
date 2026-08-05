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
     * Returns the minimal workshop snapshot needed for registration: its state (a workshop is open
     * for booking only when {@code PUBLISHED}) and its start time (used by Registration to enforce
     * its own cancellation-deadline invariant). Empty when the workshop does not exist.
     */
    Optional<WorkshopRegistrationContract> getForRegistration(UUID workshopId);

    /**
     * Acquires a pessimistic write lock on the workshop row (lock-anchor, ADR 0015) and returns the
     * same registration snapshot as {@link #getForRegistration}. Used by the Registration module's
     * capacity gate: all concurrent registrations for the same workshop serialize on this single
     * row-lock (the {@code workshops} row always exists, unlike a possibly-empty {@code registrations}
     * set), so the subsequent {@code countActiveByWorkshop} read is stable and no seat is
     * over-booked. Empty when the workshop does not exist.
     */
    Optional<WorkshopRegistrationContract> lockForRegistration(UUID workshopId);

    /**
     * Returns the workshops assigned to a given room whose time window overlaps the specified range,
     * as consumer-driven DTOs (id + state). Empty when no workshop overlaps.
     *
     * @param roomId    the room to filter by
     * @param startTime the maintenance window start (inclusive lower bound)
     * @param endTime   the maintenance window end (null = indefinite)
     */
    List<WorkshopImpactContract> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime);
}
