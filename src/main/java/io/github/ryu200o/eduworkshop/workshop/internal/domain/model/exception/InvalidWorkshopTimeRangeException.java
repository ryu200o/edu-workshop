package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

/**
 * Domain exception raised when a workshop's time range is invalid (e.g. endTime is not after
 * startTime). Thrown by the aggregate itself at creation time. This is a local invariant; no
 * global/set-based check applies (unlike uniqueness invariants which use a Domain Policy).
 */
public final class InvalidWorkshopTimeRangeException extends WorkshopDomainException {
    public InvalidWorkshopTimeRangeException(String message) {
        super(message);
    }
}
