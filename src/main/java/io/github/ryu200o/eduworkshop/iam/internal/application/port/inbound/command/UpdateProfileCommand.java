package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Self-service profile update ({@code PUT /api/v1/iam/me/profile}, OQ-5 RESOLVED). Only
 * {@code full_name} (required) plus the optional {@code phone_number} / {@code student_code} /
 * {@code avatar_url} are editable; {@code email} and {@code password} are NOT — sending them is
 * rejected by the inbound adapter before this command is ever built (the controller validates the raw
 * JSON payload), and the domain's {@code updateProfile} does not accept them at all. The caller id
 * comes from the JWT principal.
 *
 * @param userId      the authenticated caller
 * @param fullName    the new display name (required, non-blank)
 * @param phoneNumber optional contact phone
 * @param studentCode optional student code
 * @param avatarUrl   optional avatar URL
 */
public record UpdateProfileCommand(
        UUID userId,
        String fullName,
        String phoneNumber,
        String studentCode,
        String avatarUrl
) implements Command<UpdateProfileCommand.Result> {

    public record Result() {
    }
}