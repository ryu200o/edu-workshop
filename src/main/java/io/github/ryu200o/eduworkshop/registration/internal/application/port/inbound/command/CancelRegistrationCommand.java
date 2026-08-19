package io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Cancels a student's seat on a workshop. Only the seat owner may cancel ({@code userId} is compared
 * against the registration's student — an Application/orchestration concern). The 24-hour
 * cancellation deadline is enforced by the domain aggregate.
 */
public record CancelRegistrationCommand(
        UUID registrationId,
        UUID userId
) implements Command {
}
