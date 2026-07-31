package io.github.ryu200o.eduworkshop.registration.internal.domain.model.event;

import io.github.ryu200o.eduworkshop.registration.internal.domain.model.RegistrationId;
import io.github.ryu200o.eduworkshop.registration.internal.domain.model.StudentId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a student books a seat for a workshop for the first time
 * (the registration row is created with state {@code REGISTERED}).
 */
public record RegistrationCreated(
        RegistrationId registrationId,
        UUID workshopId,
        StudentId studentId,
        Instant startTime,
        Instant occurredAt
) implements RegistrationDomainEvent {
}
