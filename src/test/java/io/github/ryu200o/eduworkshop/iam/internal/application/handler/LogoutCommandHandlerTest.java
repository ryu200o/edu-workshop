package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LogoutCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutCommandHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private LogoutCommandHandler handler() {
        return new LogoutCommandHandler(refreshTokenRepository, clock);
    }

    @Test
    void logout_activeToken_revokesAndSavesIt() {
        Instant now = Instant.now(clock);
        RefreshToken token = RefreshToken.create(UserId.generate(), TokenHash.sha256Hex("raw-token"),
                now.plus(Duration.ofDays(7)), now);
        when(refreshTokenRepository.loadByHashWithLock(token.tokenHash())).thenReturn(Optional.of(token));

        handler().handle(new LogoutCommand("raw-token"));

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_unknownOrAlreadyRevokedToken_isSilentSuccess() {
        when(refreshTokenRepository.loadByHashWithLock(any())).thenReturn(Optional.empty());

        assertThatCode(() -> handler().handle(new LogoutCommand("never-issued")))
                .doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).save(any());
    }
}