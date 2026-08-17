package io.github.ryu200o.eduworkshop.iam.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationPersistenceException;

/**
 * Application-layer exception for unexpected user persistence failures that cannot be translated
 * into a specific business exception (e.g. a non-unique-constraint database error). Wraps the
 * persistence cause and identifies the resource type ("User") for consistent error reporting.
 */
public final class UserPersistenceException extends ApplicationPersistenceException {

    public UserPersistenceException(Throwable cause) {
        super("User", cause);
    }

    public UserPersistenceException(String message, Throwable cause) {
        super("User", message, cause);
    }
}
