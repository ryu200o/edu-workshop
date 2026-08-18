package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidCredentialsException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.LoginCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.exception.UserLockedException;

import org.junit.jupiter.api.BeforeEach;
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
class LoginCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessTokenCodec accessTokenCodec;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
    private final IamSecurityParameters parameters = new IamSecurityParameters("secret", 15, 7, 24, true);

    private LoginCommandHandler handler() {
        return new LoginCommandHandler(userRepository, refreshTokenRepository,
                userDomainEventPublisher, passwordEncoder, accessTokenCodec, parameters, clock);
    }

    private static User verifiedUser(Instant now) {
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash",
                "Nguyen Van A", now);
        user.verifyEmail(now);
        return user;
    }

    @Test
    void login_unknownEmail_isRejectedWithoutEnumeration() {
        when(userRepository.loadByEmail(Email.of("nobody@example.com"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new LoginCommand("nobody@example.com", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_recordsFailure_andRejects() {
        User user = verifiedUser(Instant.now(clock));
        when(userRepository.loadByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(new LoginCommand("student@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_lockedAccount_isRejected() {
        User user = verifiedUser(Instant.now(clock));
        user.recordFailedLogin(Instant.now(clock));
        user.recordFailedLogin(Instant.now(clock));
        user.recordFailedLogin(Instant.now(clock));
        user.recordFailedLogin(Instant.now(clock));
        user.recordFailedLogin(Instant.now(clock));
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        when(userRepository.loadByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> handler().handle(new LoginCommand("student@example.com", "any")))
                .isInstanceOf(UserLockedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_success_resetsLockout_andIssuesSession() {
        User user = verifiedUser(Instant.now(clock));
        when(userRepository.loadByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "$2a$12$hash")).thenReturn(true);
        when(accessTokenCodec.encode(any())).thenReturn("signed-jwt");

        LoginCommand.Result result = handler().handle(new LoginCommand("student@example.com", "Passw0rd!"));

        assertThat(result.accessToken()).isEqualTo("signed-jwt");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(900);

        ArgumentCaptor<RefreshToken> refreshCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshCaptor.capture());
        assertThat(refreshCaptor.getValue().tokenHash()).isEqualTo(TokenHash.sha256Hex(result.refreshToken()));
        assertThat(refreshCaptor.getValue().userId()).isEqualTo(user.getId());
    }
}
