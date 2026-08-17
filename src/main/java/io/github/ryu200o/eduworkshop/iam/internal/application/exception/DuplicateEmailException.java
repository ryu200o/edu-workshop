package io.github.ryu200o.eduworkshop.iam.internal.application.exception;

import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Raised when a registration / admin create-user collides with an already-registered email. This is
 * a global / set-based invariant proven by the DB unique index on {@code email} (ADR 0005 Revised):
 * the handler's {@code existsByEmail} check is the fast-fail UX gate; the adapter's
 * {@code DataIntegrityViolationException} translation is the race-proof backstop.
 */
public final class DuplicateEmailException extends ApplicationException {

    public DuplicateEmailException(Email email) {
        super("A user with email '" + email.value() + "' already exists.");
    }
}
