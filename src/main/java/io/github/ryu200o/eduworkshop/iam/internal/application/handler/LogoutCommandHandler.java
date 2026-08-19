package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Single-token logout ({@code POST /iam/auth/logout}). The presented RAW refresh token is hashed and
 * the matching row is revoked under a pessimistic write lock (serializes against a concurrent
 * {@code refresh} of the same token). Idempotent: an unknown/already-revoked token is a silent
 * success — logout never fails.
 */
@Component
class LogoutCommandHandler implements CommandHandler<LogoutCommand, LogoutCommand.Result> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    LogoutCommandHandler(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LogoutCommand.Result handle(LogoutCommand command) {
        Instant now = Instant.now(clock);
        String tokenHash = TokenHash.sha256Hex(command.refreshToken());
        refreshTokenRepository.loadByHashWithLock(tokenHash).ifPresent(token -> {
            token.revoke(now);
            refreshTokenRepository.save(token);
        });
        return new LogoutCommand.Result();
    }
}