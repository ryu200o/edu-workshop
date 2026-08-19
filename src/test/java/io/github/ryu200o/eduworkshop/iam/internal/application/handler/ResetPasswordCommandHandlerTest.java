package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ResetPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordCommandHandlerTest {

    @Mock
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private ResetPasswordCommandHandler handler() {
        return new ResetPasswordCommandHandler(oneTimeTokenRepository, userRepository,
                refreshTokenRepository, userDomainEventPublisher, passwordEncoder, clock);
    }

    private record TokenPair(String raw, OneTimeToken token) {
    }

    private TokenPair activeToken(UserId userId, Instant now) {
        String raw = TokenHash.generateRaw();
        OneTimeToken token = OneTimeToken.create(userId, TokenHash.sha256Hex(raw),
                now.plusSeconds(3600), now);
        return new TokenPair(raw, token);
    }

    @Test
    void resetPassword_updatesPassword_consumesToken_andRevokesSessions() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        TokenPair pair = activeToken(userId, now);
        User user = User.create(userId, Email.of("s@e.com"), "old-hash", "A", now);
        when(oneTimeTokenRepository.loadByHashWithLock(pair.token().tokenHash()))
                .thenReturn(Optional.of(pair.token()));
        when(userRepository.loadByIdWithLock(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass!23")).thenReturn("$2a$12$newHash");

        handler().handle(new ResetPasswordCommand(pair.raw(), "NewPass!23"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHash");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllActiveByUserId(userId, now);

        ArgumentCaptor<OneTimeToken> tokenCaptor = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(oneTimeTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();
    }

    @Test
    void resetPassword_unknownToken_isRejected() {
        when(oneTimeTokenRepository.loadByHashWithLock(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new ResetPasswordCommand("garbage", "NewPass!23")))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void resetPassword_usedToken_isRejected() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        TokenPair pair = activeToken(userId, now);
        pair.token().markUsed(now);
        when(oneTimeTokenRepository.loadByHashWithLock(pair.token().tokenHash()))
                .thenReturn(Optional.of(pair.token()));

        assertThatThrownBy(() -> handler().handle(new ResetPasswordCommand(pair.raw(), "NewPass!23")))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }
}
