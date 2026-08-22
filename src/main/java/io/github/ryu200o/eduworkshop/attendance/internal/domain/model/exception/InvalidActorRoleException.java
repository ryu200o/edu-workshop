package io.github.ryu200o.eduworkshop.attendance.internal.domain.model.exception;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;

/**
 * Raised when an attendance decision is attempted by an actor whose {@link ActorRole} is not
 * permitted for that operation (e.g. marking attendance as a STUDENT, or adjusting as a TRAINER).
 *
 * <p>This is a domain invariant (self-defending aggregate, ADR 0019 §8): the role authorization
 * is also enforced at the Application boundary (403), but the aggregate refuses to record a ledger
 * entry for an unauthorized actor even if reached directly. Mapped to HTTP 403 by
 * {@code AttendanceExceptionAdvice}.</p>
 */
public final class InvalidActorRoleException extends AttendanceDomainException {

    public InvalidActorRoleException(ActorRole actual, ActorRole expected) {
        super("Actor role '%s' is not authorized for this attendance operation; required role: '%s'"
                .formatted(actual, expected));
    }

    public InvalidActorRoleException(String message) {
        super(message);
    }
}
