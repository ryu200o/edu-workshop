package io.github.ryu200o.eduworkshop.shared.application.exception;

/**
 * Base type for all application-layer exceptions in the shared kernel. Unchecked by design:
 * application failures (validation, not-found, persistence) are programming/structural errors that
 * should propagate to the HTTP boundary rather than be forced into every method signature.
 *
 * <p>This hierarchy separates <em>application concerns</em> (orchestration, persistence, lookup
 * failures) from <em>domain invariants</em> (which live in each module's domain exception hierarchy).
 * Concrete subtypes: {@link ResourceNotFoundException} and {@link ApplicationPersistenceException}.
 */
public abstract class ApplicationException extends RuntimeException {
    protected ApplicationException(String message) {
        super(message);
    }
    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
