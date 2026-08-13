package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

/**
 * Base type for all business-rule violations raised by the Attendance domain.
 *
 * <p>Unchecked by design: a violated invariant is a programming/domain error that should surface
 * immediately rather than be forced into every signature.</p>
 *
 * <p>NOTE: a failed lookup / not-found or a role violation is an <em>application</em> concern, not a
 * domain invariant, and therefore lives in {@code attendance.internal.application.exception}. The
 * domain never imports it.</p>
 */
public abstract class AttendanceDomainException extends RuntimeException {

    protected AttendanceDomainException(String message) {
        super(message);
    }

    protected AttendanceDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}