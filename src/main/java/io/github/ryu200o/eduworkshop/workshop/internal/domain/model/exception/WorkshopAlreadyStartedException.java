package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Raised when a {@code cancel} attempt is made on a PUBLISHED workshop whose start time has already
 * passed (or is now) — a session that is ongoing or finished must not be cancelled.
 */
public class WorkshopAlreadyStartedException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final Instant startTime;
    private final Instant now;

    public WorkshopAlreadyStartedException(WorkshopId workshopId, Instant startTime, Instant now) {
        super("Cannot cancel workshop " + workshopId.value()
                + " because it has already started at " + startTime
                + " (current time: " + now + ")");
        this.workshopId = workshopId;
        this.startTime = startTime;
        this.now = now;
    }

    public WorkshopId workshopId() {
        return workshopId;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant now() {
        return now;
    }
}
