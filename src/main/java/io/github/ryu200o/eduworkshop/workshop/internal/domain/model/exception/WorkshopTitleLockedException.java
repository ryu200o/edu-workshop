package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

/**
 * Raised when an attempt is made to change the title of a {@code PUBLISHED} workshop
 * that already has active registrations.
 *
 * <p>Title is immutable once students hold seats, to prevent topic drift / fraud on
 * electronic tickets. The Organizer must cancel the workshop and create a new one if
 * the title truly needs to change.</p>
 */
public final class WorkshopTitleLockedException extends WorkshopDomainException {

    private final WorkshopId workshopId;
    private final int activeRegistrations;

    public WorkshopTitleLockedException(WorkshopId workshopId, int activeRegistrations) {
        super("Workshop " + workshopId.value() + " has " + activeRegistrations
                + " active registration(s); title is locked for PUBLISHED workshops with active registrations.");
        this.workshopId = workshopId;
        this.activeRegistrations = activeRegistrations;
    }

    public WorkshopId getWorkshopId() {
        return workshopId;
    }

    public int getActiveRegistrations() {
        return activeRegistrations;
    }
}