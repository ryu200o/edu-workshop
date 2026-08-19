package io.github.ryu200o.eduworkshop.iam.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Raised when a presented token (access JWT, refresh token, or one-time action token) is unknown,
 * expired, already used/revoked, or malformed. Mapped to HTTP 401.
 */
public final class InvalidTokenException extends ApplicationException {

    public InvalidTokenException() {
        super("The provided token is invalid or has expired.");
    }
}
