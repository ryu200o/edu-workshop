package io.github.ryu200o.eduworkshop.registration.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

/**
 * Application-layer exception raised when a student tries to book a seat on a workshop they already
 * hold an active ({@code REGISTERED}) registration for.
 *
 * <p>This is a <em>global / set-based</em> rule (ADR 0005) — it cannot be proven by a single
 * aggregate — so it is orchestrated by the Application handler (fast-fail read) and backed by the
 * DB unique index {@code uk_registrations_workshop_user} (race-proof backstop): the write adapter
 * translates a {@code DataIntegrityViolationException} into this exception.</p>
 */
public final class DuplicateRegistrationException extends ApplicationException {

    public DuplicateRegistrationException(java.util.UUID workshopId, java.util.UUID userId) {
        super("User %s already has an active registration for workshop %s".formatted(userId, workshopId));
    }
}
