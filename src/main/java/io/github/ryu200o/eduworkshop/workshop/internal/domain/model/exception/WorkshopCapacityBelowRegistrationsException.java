package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

/**
 * Raised when an {@code adjustCapacity} attempt would lower a PUBLISHED workshop's capacity below
 * the number of currently active registrations. The active-registration count is orchestrated by the
 * Application handler (ADR 0005) and passed in as data — the aggregate validates the relation as a
 * local invariant.
 */
public class WorkshopCapacityBelowRegistrationsException extends WorkshopDomainException {

    private final int workshopCapacity;
    private final int activeRegistrations;

    public WorkshopCapacityBelowRegistrationsException(int workshopCapacity, int activeRegistrations) {
        super("New capacity (" + workshopCapacity + ") cannot be less than currently active registrations (" + activeRegistrations + ")");
        this.workshopCapacity = workshopCapacity;
        this.activeRegistrations = activeRegistrations;
    }

    public int workshopCapacity() {
        return workshopCapacity;
    }

    public int activeRegistrations() {
        return activeRegistrations;
    }
}
