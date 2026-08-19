package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidCredentialsException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenResponse;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth.AuthTokenUseCase;
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

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link AuthTokenUseCase} security-ingress port (ADR 0021): password login and
 * refresh-token rotation (RTR). These mint access/refresh sessions and are deliberately NOT commands
 * on the strictly-void {@code CommandBus}.
 *
 * <p>Login: unknown email and wrong password both surface as {@link InvalidCredentialsException}
 * (no user enumeration). The status gate (LOCKED → {@code UserLockedException},
 * PENDING_VERIFICATION/DISABLED → illegal state) is enforced by the aggregate during
 * {@code recordFailedLogin}/{@code recordSuccessfulLogin} (ADR 0020 §1.5). A successful login resets
 * the lockout streak and issues a fresh access + refresh session.</p>
 *
 * <p>Refresh: the presented token is loaded under a pessimistic write lock so concurrent refreshes
 * serialize. Replay of a revoked token triggers the family revoke (RFC 6819) — a single bulk UPDATE
 * revokes all still-active tokens of the user. A LOCKED/DISABLED account cannot refresh.</p>
 */
@Service
class AuthTokenService implements AuthTokenUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenCodec accessTokenCodec;
    private final IamSecurityParameters parameters;
    private final Clock clock;

    AuthTokenService(UserRepository userRepository,
                     RefreshTokenRepository refreshTokenRepository,
                     UserDomainEventPublisher userDomainEventPublisher,
                     PasswordEncoder passwordEncoder,
                     AccessTokenCodec accessTokenCodec,
                     IamSecurityParameters parameters,
                     Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenCodec = accessTokenCodec;
        this.parameters = parameters;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokenResponse login(String email, String password) {
        Instant now = Instant.now(clock);
        Email parsedEmail = Email.of(email);

        User user = userRepository.loadByEmail(parsedEmail).orElseThrow(InvalidCredentialsException::new);
        user.assertNotLocked(now);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now);
            userRepository.save(user);
            userDomainEventPublisher.publish(user.recordedEvents());
            user.clearRecordedEvents();
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();

        String accessToken = accessTokenCodec.encode(toClaims(user, now, parameters.accessTtlMinutes()));
        String rawRefreshToken = issueRefreshToken(user.getId(), now);
        long expiresInSeconds = Duration.ofMinutes(parameters.accessTtlMinutes()).toSeconds();

        return new AuthTokenResponse(accessToken, rawRefreshToken, expiresInSeconds, user.isMustChangePassword());
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthTokenResponse refresh(String refreshToken) {
        Instant now = Instant.now(clock);
        String tokenHash = TokenHash.sha256Hex(refreshToken);

        RefreshToken token = refreshTokenRepository.loadByHashWithLock(tokenHash)
                .orElseThrow(InvalidTokenException::new);

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(token.userId(), now);
            throw new InvalidTokenException();
        }
        if (token.isExpired(now)) {
            throw new InvalidTokenException();
        }

        User user = userRepository.loadByIdWithLock(token.userId())
                .orElseThrow(() -> new UserNotFoundException(token.userId()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw new InvalidTokenException();
        }

        token.revoke(now);
        refreshTokenRepository.save(token);

        String accessToken = accessTokenCodec.encode(toClaims(user, now, parameters.accessTtlMinutes()));
        String rawRefreshToken = issueRefreshToken(user.getId(), now);

        return new AuthTokenResponse(accessToken, rawRefreshToken,
                Duration.ofMinutes(parameters.accessTtlMinutes()).toSeconds(), user.isMustChangePassword());
    }

    /**
     * Mints, persists, and returns the RAW refresh token (the client sees it exactly once; only its
     * SHA-256 hash is stored).
     */
    private String issueRefreshToken(UserId userId, Instant now) {
        String raw = TokenHash.generateRaw();
        RefreshToken token = RefreshToken.create(
                userId,
                TokenHash.sha256Hex(raw),
                now.plus(Duration.ofDays(parameters.refreshTtlDays())),
                now
        );
        refreshTokenRepository.save(token);
        return raw;
    }

    private static AccessTokenCodec.AccessTokenClaims toClaims(User user, Instant now, int accessTtlMinutes) {
        return new AccessTokenCodec.AccessTokenClaims(
                user.getId().value(),
                user.getEmail().value(),
                toRoleNames(user),
                user.isMustChangePassword(),
                now,
                now.plus(Duration.ofMinutes(accessTtlMinutes))
        );
    }

    private static Set<String> toRoleNames(User user) {
        return user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    }
}