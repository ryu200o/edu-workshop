package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationState;

/**
 * Status values a learner may use to filter their "My Bookings" list, plus the states the read view
 * can display.
 *
 * <p>The <em>user-selectable filter</em> is restricted to {@code REGISTERED} and {@code CANCELLED}:
 * {@code REFUNDED} is a system-initiated outcome (workshop cancelled) and {@code VERIFIED} is the
 * verified-seat state, so both are deliberately not filters a learner may choose. The view enum
 * still mirrors {@link RegistrationState} so that a non-filtered query (Epic 2 sign-off Q4) can
 * display the full history including {@code REFUNDED} and {@code VERIFIED}.</p>
 */
public enum MyRegistrationStatus {
    REGISTERED,
    CANCELLED,
    REFUNDED,
    VERIFIED
}
