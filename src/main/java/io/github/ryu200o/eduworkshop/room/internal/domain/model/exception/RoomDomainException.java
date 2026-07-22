package io.github.ryu200o.eduworkshop.room.internal.domain.model.exception;

/**
 * Base type for all business-rule violations raised by the Room domain.
 *
 * <p>Unchecked by design: a violated invariant is a programming/domain error that should
 * surface immediately rather than be forced into every signature.</p>
 */
public abstract class RoomDomainException extends RuntimeException {

    protected RoomDomainException(String message) {
        super(message);
    }

    protected RoomDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
