package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutAllCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Global logout ({@code POST /iam/me/logout-all}). Revokes every active refresh token of the caller
 * in a single bulk UPDATE (RFC 6819 family protection) — used for "log out on all devices". Idempotent.
 */
@Component
class LogoutAllCommandHandler implements CommandHandler<LogoutAllCommand, LogoutAllCommand.Result> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    LogoutAllCommandHandler(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LogoutAllCommand.Result handle(LogoutAllCommand command) {
        refreshTokenRepository.revokeAllActiveByUserId(UserId.of(command.userId()), Instant.now(clock));
        return new LogoutAllCommand.Result();
    }
}