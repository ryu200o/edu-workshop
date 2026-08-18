package io.github.ryu200o.eduworkshop.iam.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Raised by the login handler when the submitted email/password pair does not authenticate — covering
 * both an unknown email and a wrong password with an identical message to avoid user enumeration
 * (ADR 0020 §1.5). Mapped to HTTP 401.
 */
public final class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
