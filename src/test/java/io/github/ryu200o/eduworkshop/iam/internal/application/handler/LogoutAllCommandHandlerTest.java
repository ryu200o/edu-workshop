package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutAllCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogoutAllCommandHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private LogoutAllCommandHandler handler() {
        return new LogoutAllCommandHandler(refreshTokenRepository, clock);
    }

    @Test
    void logoutAll_revokesEveryActiveRefreshTokenOfTheUser() {
        UUID userId = UUID.randomUUID();

        handler().handle(new LogoutAllCommand(userId));

        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(UserId.of(userId)), any());
    }
}