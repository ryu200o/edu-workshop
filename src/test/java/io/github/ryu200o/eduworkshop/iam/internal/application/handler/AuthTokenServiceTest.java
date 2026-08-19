package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidCredentialsException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenResponse;
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

/**
 * Unit tests for {@link AuthTokenService} — the Security Token Minting operations (login + RTR
 * refresh) behind the {@link io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenUseCase}
 * port (ADR 0021: these bypass the strictly-void CommandBus).
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

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

    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        service = new AuthTokenService(userRepository, refreshTokenRepository, userDomainEventPublisher,
                passwordEncoder, accessTokenCodec, parameters, clock);
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

        assertThatThrownBy(() -> service.login("nobody@example.com", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_recordsFailure_andRejects() {
        User user = verifiedUser(Instant.now(clock));
        when(userRepository.loadByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("student@example.com", "wrong"))
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

        assertThatThrownBy(() -> service.login("student@example.com", "any"))
                .isInstanceOf(UserLockedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_success_resetsLockout_andIssuesSession() {
        User user = verifiedUser(Instant.now(clock));
        when(userRepository.loadByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "$2a$12$hash")).thenReturn(true);
        when(accessTokenCodec.encode(any())).thenReturn("signed-jwt");

        AuthTokenResponse result = service.login("student@example.com", "Passw0rd!");

        assertThat(result.accessToken()).isEqualTo("signed-jwt");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(result.mustChangePassword()).isFalse();

        ArgumentCaptor<RefreshToken> refreshCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshCaptor.capture());
        assertThat(refreshCaptor.getValue().tokenHash()).isEqualTo(TokenHash.sha256Hex(result.refreshToken()));
        assertThat(refreshCaptor.getValue().userId()).isEqualTo(user.getId());
    }

    @Test
    void refresh_unknownToken_isRejected() {
        when(refreshTokenRepository.loadByHashWithLock(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_revokedToken_revokesFamily_andRejects() {
        User user = verifiedUser(Instant.now(clock));
        RefreshToken token = RefreshToken.create(
                user.getId(),
                TokenHash.sha256Hex("revoked-token"),
                Instant.now(clock).plusSeconds(3600),
                Instant.now(clock));
        token.revoke(Instant.now(clock));
        when(refreshTokenRepository.loadByHashWithLock(TokenHash.sha256Hex("revoked-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("revoked-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllActiveByUserId(user.getId(), Instant.now(clock));
    }

    @Test
    void refresh_success_rotatesSession() {
        User user = verifiedUser(Instant.now(clock));
        RefreshToken token = RefreshToken.create(
                user.getId(),
                TokenHash.sha256Hex("old-token"),
                Instant.now(clock).plusSeconds(3600),
                Instant.now(clock));
        when(refreshTokenRepository.loadByHashWithLock(TokenHash.sha256Hex("old-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.loadByIdWithLock(user.getId())).thenReturn(Optional.of(user));
        when(accessTokenCodec.encode(any())).thenReturn("signed-jwt");

        AuthTokenResponse result = service.refresh("old-token");

        assertThat(result.accessToken()).isEqualTo("signed-jwt");
        assertThat(result.refreshToken()).isNotBlank();

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
        ArgumentCaptor<RefreshToken> newTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(newTokenCaptor.capture());
        RefreshToken newToken = newTokenCaptor.getAllValues().stream()
                .filter(candidate -> candidate != token)
                .findFirst()
                .orElseThrow();
        assertThat(newToken.tokenHash()).isEqualTo(TokenHash.sha256Hex(result.refreshToken()));
        assertThat(newToken.userId()).isEqualTo(user.getId());
    }

    @Test
    void refresh_nonActiveUser_isRejected() {
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash",
                "Nguyen Van A", Instant.now(clock));
        RefreshToken token = RefreshToken.create(
                user.getId(),
                TokenHash.sha256Hex("token"),
                Instant.now(clock).plusSeconds(3600),
                Instant.now(clock));
        when(refreshTokenRepository.loadByHashWithLock(TokenHash.sha256Hex("token")))
                .thenReturn(Optional.of(token));
        when(userRepository.loadByIdWithLock(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refresh("token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllActiveByUserId(user.getId(), Instant.now(clock));
    }
}