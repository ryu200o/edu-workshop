package io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception;

/**
 * Base type for all business-rule violations raised by the IAM domain.
 *
 * <p>Unchecked by design: a violated invariant is a programming/domain error that should
 * surface immediately rather than be forced into every signature.</p>
 */
public abstract class UserDomainException extends RuntimeException {

    protected UserDomainException(String message) {
        super(message);
    }

    protected UserDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}