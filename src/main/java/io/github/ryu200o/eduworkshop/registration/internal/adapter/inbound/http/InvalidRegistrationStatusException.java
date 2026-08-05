package io.github.ryu200o.eduworkshop.registration.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;

/**
 * Raised by {@link RegistrationQueryController} when a learner requests a status filter that is not
 * user-selectable. {@code REFUNDED} is a system-initiated outcome (workshop cancelled), so it is
 * deliberately excluded from the user-facing filter options (Epic 2 sign-off Q4). Mapped to HTTP 400.
 */
class InvalidRegistrationStatusException extends IllegalArgumentException {

    InvalidRegistrationStatusException(MyRegistrationStatus status) {
        super("Status '%s' is not a user-selectable filter (choose REGISTERED or CANCELLED)."
                .formatted(status));
    }
}
