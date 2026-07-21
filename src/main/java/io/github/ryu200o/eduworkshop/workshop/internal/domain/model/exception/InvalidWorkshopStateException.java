package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;

/**
 * Raised when a requested lifecycle transition is rejected by the workshop's invariant.
 *
 * <p>Examples: calling {@code schedule()} on a non-DRAFT workshop (re-scheduling is a future
 * {@code reschedule()} slice), or calling {@code publish()} on a workshop that is not SCHEDULED
 * (e.g. still DRAFT).</p>
 */
public class InvalidWorkshopStateException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final WorkshopState currentState;
    private final WorkshopState attemptedState;

    public InvalidWorkshopStateException(WorkshopId workshopId,
                                         WorkshopState currentState,
                                         WorkshopState attemptedState,
                                         String message) {
        super(message);
        this.workshopId = workshopId;
        this.currentState = currentState;
        this.attemptedState = attemptedState;
    }

    public WorkshopId getWorkshopId() {
        return workshopId;
    }

    public WorkshopState getCurrentState() {
        return currentState;
    }

    public WorkshopState getAttemptedState() {
        return attemptedState;
    }
}
