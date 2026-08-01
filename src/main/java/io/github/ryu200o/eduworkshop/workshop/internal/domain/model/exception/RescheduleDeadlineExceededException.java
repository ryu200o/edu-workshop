package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.time.Instant;

/**
 * Raised when a workshop is rescheduled too close to its start time.
 *
 * <p>To respect the customers' schedule, a {@code PUBLISHED} workshop can only be rescheduled while
 * {@code now <= startTime − RESCHEDULE_DEADLINE} (24h). This is a local business invariant of the
 * aggregate — mirroring {@code Registration}'s {@code CancellationDeadlineExceededException} — not a
 * deployment parameter.</p>
 */
public final class RescheduleDeadlineExceededException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final Instant deadline;
    private final Instant attemptedAt;

    public RescheduleDeadlineExceededException(WorkshopId workshopId,
                                               Instant deadline,
                                               Instant attemptedAt) {
        super("Workshop " + workshopId.value() + " can only be rescheduled no later than "
                + deadline + " (" + Workshop.RESCHEDULE_DEADLINE.toHours() + "h before it starts); "
                + "attempted at " + attemptedAt + ".");
        this.workshopId = workshopId;
        this.deadline = deadline;
        this.attemptedAt = attemptedAt;
    }

    public WorkshopId getWorkshopId() {
        return workshopId;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
