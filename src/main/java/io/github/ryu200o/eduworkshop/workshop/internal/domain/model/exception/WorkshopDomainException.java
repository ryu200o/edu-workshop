package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

/**
 * Base type for all business-rule violations raised by the Workshop domain.
 *
 * <p>Unchecked by design: a violated invariant is a programming/domain error that should surface
 * immediately rather than be forced into every signature.</p>
 *
 * <p>NOTE: a failed lookup / not-found is an <em>application</em> concern, not a domain invariant, and
 * therefore lives in {@code workshop.internal.application.exception} (extending the shared
 * {@code ResourceNotFoundException}). The domain never imports it.</p>
 */
public class WorkshopDomainException extends RuntimeException {

    public WorkshopDomainException(String message) {
        super(message);
    }

    public WorkshopDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
