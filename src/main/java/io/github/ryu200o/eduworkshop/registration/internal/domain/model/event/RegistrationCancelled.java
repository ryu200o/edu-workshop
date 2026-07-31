package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a student cancels their seat (state REGISTERED → CANCELLED).
 *
 * <p>Only reachable when the cancellation happens before the 24-hour deadline enforced by the
 * aggregate (otherwise {@code CancellationDeadlineExceededException} is raised and no event fires).</p>
 */
public record RegistrationCancelled(
        RegistrationId registrationId,
        UUID workshopId,
        StudentId studentId,
        Instant occurredAt
) implements RegistrationDomainEvent {
}
