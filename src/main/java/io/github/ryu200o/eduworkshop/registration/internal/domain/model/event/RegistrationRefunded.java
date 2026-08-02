package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a system-initiated refund flips a registration from
 * {@code REGISTERED} to {@code REFUNDED} (the workshop was cancelled by the organizer).
 *
 * <p>Distinct from {@link RegistrationCancelled}: the latter represents a student's
 * voluntary cancellation (with deadline check), while this event represents a system
 * forced refund. Downstream consumers (Analytics, Notification) can distinguish the two
 * origins for accurate reporting.</p>
 */
public record RegistrationRefunded(
        RegistrationId registrationId,
        UUID workshopId,
        StudentId studentId,
        Instant occurredAt
) implements RegistrationDomainEvent {
}