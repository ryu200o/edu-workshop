package io.github.ryu200o.eduworkshop.registration.internal.domain.model.exception;

/**
 * Base type for all business-rule violations raised by the Registration domain.
 *
 * <p>Unchecked by design: a violated invariant is a programming/domain error that should surface
 * immediately rather than be forced into every signature.</p>
 *
 * <p>NOTE: a failed lookup / not-found is an <em>application</em> concern, not a domain invariant, and
 * therefore lives in {@code registration.internal.application.exception} (extending the shared
 * {@code ResourceNotFoundException}) in a later slice. The domain never imports it.</p>
 */
public abstract class RegistrationDomainException extends RuntimeException {

    protected RegistrationDomainException(String message) {
        super(message);
    }

    protected RegistrationDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
