package io.github.ryu200o.eduworkshop.shared.application.exception;

/**
 * Base exception for persistence failures that cannot be translated into a
 * more specific business exception.
 *
 * <p>The infrastructure layer should first try to translate known database
 * constraint violations into domain vocabulary. This exception is only used
 * as a fallback when no meaningful business mapping exists.</p>
 */
public abstract class ApplicationPersistenceException extends ApplicationException {
    protected ApplicationPersistenceException(String resourceType, Throwable cause) {
        super("Failed to persist %s.".formatted(resourceType), cause);
    }

    protected ApplicationPersistenceException(String resourceType,
                                              String message,
                                              Throwable cause) {
        super("%s: %s".formatted(resourceType, message), cause);
    }
}
