package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a previously cancelled registration is re-activated
 * (state CANCELLED → REGISTERED on the same row).
 *
 * <p>Kept as a <em>distinct</em> event type from {@link RegistrationCreated} (per the SA+PO review
 * decision) so downstream Analytics/Notification can tell a brand-new booking apart from a renewal of
 * an old ticket. Carries the refreshed {@code startTime} snapshot.</p>
 */
public record RegistrationReactivated(
        RegistrationId registrationId,
        UUID workshopId,
        StudentId studentId,
        Instant startTime,
        Instant occurredAt
) implements RegistrationDomainEvent {
}
