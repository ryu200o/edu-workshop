package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side projection of a learner's booking for the "My Bookings" query. Backed entirely by
 * selectively-snapshotted columns on the {@code registrations} table (ADR 0007) — the read path is a
 * single self-contained SELECT with no cross-module JOIN. Snapshot columns may be {@code null} for
 * rows created before V15; the HTTP layer falls back to empty strings for display.
 */
public record MyRegistrationView(
        UUID registrationId,
        UUID workshopId,
        String workshopTitle,
        Instant workshopStartTime,
        Instant workshopEndTime,
        String workshopRoomName,
        UUID userId,
        MyRegistrationStatus status,
        Instant registeredAt,
        Instant cancelledAt
) {
}
