package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Raised when a {@code complete} attempt is made on an IN_PROGRESS workshop whose end time has
 * not yet been reached ({@code now < endTime}). Strict guard (D2: a session may not be completed
 * before it is due to end).
 */
public class WorkshopCompletionNotDueException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final Instant endTime;
    private final Instant now;

    public WorkshopCompletionNotDueException(WorkshopId workshopId, Instant endTime, Instant now) {
        super("Cannot complete workshop " + workshopId.value()
                + ": its end time has not been reached (end=" + endTime
                + ", current time=" + now + ")");
        this.workshopId = workshopId;
        this.endTime = endTime;
        this.now = now;
    }

    public WorkshopId workshopId() {
        return workshopId;
    }

    public Instant endTime() {
        return endTime;
    }

    public Instant now() {
        return now;
    }
}