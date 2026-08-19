package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.util.UUID;

/**
 * Write command for public self-registration (plan §2.1, OQ-3). Raw input only — normalization/
 * validation is performed by the domain {@code Email} VO and the handler. The {@code userId} is
 * caller-generated (ADR 0021 Caller-Generated ID): the inbound adapter assigns it and the handler
 * persists the aggregate under that id. The OneTimeToken is minted against this id (ADR 0021 — the
 * raw verify token is never returned over HTTP).
 *
 * @param userId   the caller-generated aggregate id
 * @param email    the login email (LOWER-normalized by the domain)
 * @param password the raw password (hashed with BCrypt by the handler)
 * @param fullName the display name
 */
public record RegisterCommand(UUID userId, String email, String password, String fullName) implements Command {
}
