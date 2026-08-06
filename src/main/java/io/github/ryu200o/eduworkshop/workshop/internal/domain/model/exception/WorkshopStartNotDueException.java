package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Raised when a {@code start} attempt is made on a PUBLISHED workshop whose start time has
 * not yet been reached ({@code now < startTime}). Strict start guard (D1): a session must not
 * begin early, protecting the late-booked {@code BOOK} flow.
 */
public class WorkshopStartNotDueException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final Instant startTime;
    private final Instant now;

    public WorkshopStartNotDueException(WorkshopId workshopId, Instant startTime, Instant now) {
        super("Cannot start workshop " + workshopId.value()
                + " because its start time has not been reached (start=" + startTime
                + ", current time=" + now + ")");
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