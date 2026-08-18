package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command for public self-registration (plan §2.1). Raw input only — normalization/validation
 * is performed by the domain {@code Email} VO and the handler.
 *
 * @param email    the login email (LOWER-normalized by the domain)
 * @param password the raw password (hashed with BCrypt by the handler)
 * @param fullName the display name
 */
public record RegisterCommand(String email, String password, String fullName)
        implements Command<RegisterCommand.Result> {

    /**
     * @param userId      the id minted for the new account
     * @param verifyToken the RAW one-time verify-email token. Dev seam per plan §1.2 line 34
     *                    ("tạo token + trả về qua response") until real SMTP is integrated; in
     *                    production this moves to an email/event channel.
     */
    public record Result(UUID userId, String verifyToken) {
    }
}
