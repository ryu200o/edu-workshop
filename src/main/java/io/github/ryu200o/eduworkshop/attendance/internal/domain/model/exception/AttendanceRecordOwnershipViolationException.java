package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

/**
 * Raised when an attendance decision is attempted by an actor who is not the owner of the record
 * (Insecure Direct Object Reference defense). An appeal, for example, may only be submitted by the
 * learner whose attendance is being appealed — never by a different student.
 *
 * <p>This is a domain invariant (self-defending aggregate, ADR 0019 §8); the Application layer may
 * additionally enforce ownership, but the aggregate rejects cross-owner mutations on its own.
 * Mapped to HTTP 403 by {@code AttendanceExceptionAdvice}.</p>
 */
public final class AttendanceRecordOwnershipViolationException extends AttendanceDomainException {

    public AttendanceRecordOwnershipViolationException() {
        super("Actor is not the owner of this attendance record (IDOR rejected).");
    }
}
