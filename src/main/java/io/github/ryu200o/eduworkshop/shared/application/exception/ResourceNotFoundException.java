package io.github.ryu200o.eduworkshop.shared.application.exception;

/**
 * Base exception for application-layer "resource not found" scenarios.
 *
 * <p>This exception represents an application concern (failed lookup/orchestration),
 * not a domain invariant violation.</p>
 */
public abstract class ResourceNotFoundException extends RuntimeException {

    protected ResourceNotFoundException(String resourceType, String field, Object value) {
        super("%s not found by %s: %s".formatted(resourceType, field, value));
    }
}