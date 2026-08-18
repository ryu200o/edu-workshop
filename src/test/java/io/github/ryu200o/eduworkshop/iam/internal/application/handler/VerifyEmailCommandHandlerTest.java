package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.VerifyEmailCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyEmailCommandHandlerTest {

    @Mock
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private VerifyEmailCommandHandler handler() {
        return new VerifyEmailCommandHandler(oneTimeTokenRepository, userRepository,
                userDomainEventPublisher, clock);
    }

    @Test
    void verify_activatesAccount_andConsumesToken() {
        UserId userId = UserId.generate();
        String raw = TokenHash.generateRaw();
        Instant now = Instant.now(clock);
        OneTimeToken token = OneTimeToken.create(userId, TokenHash.sha256Hex(raw),
                now.plusSeconds(3600), now);
        User user = User.create(userId, io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email.of("s@e.com"),
                "hash", "A", now);
        when(oneTimeTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.of(token));
        when(userRepository.loadByIdWithLock(userId)).thenReturn(Optional.of(user));

        handler().handle(new VerifyEmailCommand(raw));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        ArgumentCaptor<OneTimeToken> tokenCaptor = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(oneTimeTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void verify_unknownToken_isRejected() {
        String raw = TokenHash.generateRaw();
        when(oneTimeTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new VerifyEmailCommand(raw)))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_usedToken_isRejected() {
        UserId userId = UserId.generate();
        String raw = TokenHash.generateRaw();
        Instant now = Instant.now(clock);
        OneTimeToken token = OneTimeToken.create(userId, TokenHash.sha256Hex(raw),
                now.plusSeconds(3600), now);
        token.markUsed(now);
        when(oneTimeTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> handler().handle(new VerifyEmailCommand(raw)))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_expiredToken_isRejected() {
        UserId userId = UserId.generate();
        String raw = TokenHash.generateRaw();
        Instant issued = Instant.now(clock);
        OneTimeToken token = OneTimeToken.create(userId, TokenHash.sha256Hex(raw),
                issued.minusSeconds(10), issued.minusSeconds(20));
        when(oneTimeTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> handler().handle(new VerifyEmailCommand(raw)))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_missingOwner_isRejected() {
        UserId userId = UserId.generate();
        String raw = TokenHash.generateRaw();
        Instant now = Instant.now(clock);
        OneTimeToken token = OneTimeToken.create(userId, TokenHash.sha256Hex(raw),
                now.plusSeconds(3600), now);
        when(oneTimeTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.of(token));
        when(userRepository.loadByIdWithLock(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new VerifyEmailCommand(raw)))
                .isInstanceOf(io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException.class);
        verify(oneTimeTokenRepository, never()).save(any());
    }
}
