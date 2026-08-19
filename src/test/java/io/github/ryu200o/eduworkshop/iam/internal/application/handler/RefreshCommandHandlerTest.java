package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RefreshCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RefreshCommandHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessTokenCodec accessTokenCodec;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
    private final IamSecurityParameters parameters = new IamSecurityParameters("secret", 15, 7, 24, true);

    private RefreshCommandHandler handler() {
        return new RefreshCommandHandler(refreshTokenRepository, userRepository,
                accessTokenCodec, parameters, clock);
    }

    /** A refresh token paired with its raw value (the handler hashes the raw to look it up). */
    private record TokenPair(String raw, RefreshToken token) {
    }

    private TokenPair activeToken(UserId userId, Instant now) {
        String raw = TokenHash.generateRaw();
        RefreshToken token = RefreshToken.create(userId, TokenHash.sha256Hex(raw),
                now.plusSeconds(3600), now);
        return new TokenPair(raw, token);
    }

    private static User verifiedUser(UserId userId, Instant now) {
        User user = User.create(userId, Email.of("s@e.com"), "hash", "A", now);
        user.verifyEmail(now);
        return user;
    }

    @Test
    void refresh_success_rotatesToken_andIssuesNewPair() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        TokenPair pair = activeToken(userId, now);
        when(refreshTokenRepository.loadByHashWithLock(pair.token().tokenHash()))
                .thenReturn(Optional.of(pair.token()));
        when(userRepository.loadByIdWithLock(userId)).thenReturn(Optional.of(verifiedUser(userId, now)));
        when(accessTokenCodec.encode(any())).thenReturn("new-jwt");

        RefreshCommand.Result result = handler().handle(new RefreshCommand(pair.raw()));

        assertThat(result.accessToken()).isEqualTo("new-jwt");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(pair.raw());
        assertThat(pair.token().isRevoked()).isTrue();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).contains(pair.token());
        assertThat(saved.getAllValues())
                .extracting(RefreshToken::tokenHash)
                .contains(TokenHash.sha256Hex(result.refreshToken()));
    }

    @Test
    void refresh_revokedToken_revokesFamily_andRejects() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        TokenPair pair = activeToken(userId, now);
        pair.token().revoke(now);
        when(refreshTokenRepository.loadByHashWithLock(pair.token().tokenHash()))
                .thenReturn(Optional.of(pair.token()));

        assertThatThrownBy(() -> handler().handle(new RefreshCommand(pair.raw())))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllActiveByUserId(userId, now);
        verify(userRepository, never()).loadByIdWithLock(any());
    }

    @Test
    void refresh_expiredToken_isRejected() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        String raw = TokenHash.generateRaw();
        RefreshToken token = RefreshToken.create(userId, TokenHash.sha256Hex(raw),
                now.minusSeconds(10), now.minusSeconds(20));
        when(refreshTokenRepository.loadByHashWithLock(TokenHash.sha256Hex(raw)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> handler().handle(new RefreshCommand(raw)))
                .isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void refresh_disabledAccount_revokesFamily_andRejects() {
        Instant now = Instant.now(clock);
        UserId userId = UserId.generate();
        TokenPair pair = activeToken(userId, now);
        User user = verifiedUser(userId, now);
        user.disable(now);
        when(refreshTokenRepository.loadByHashWithLock(pair.token().tokenHash()))
                .thenReturn(Optional.of(pair.token()));
        when(userRepository.loadByIdWithLock(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> handler().handle(new RefreshCommand(pair.raw())))
                .isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository).revokeAllActiveByUserId(userId, now);
    }

    @Test
    void refresh_unknownToken_isRejected() {
        when(refreshTokenRepository.loadByHashWithLock(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new RefreshCommand("garbage")))
                .isInstanceOf(InvalidTokenException.class);
    }
}
