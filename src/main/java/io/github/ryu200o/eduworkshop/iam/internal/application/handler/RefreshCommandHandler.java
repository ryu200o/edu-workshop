package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RefreshCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.RefreshToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refresh-token rotation (RTR, plan §2.3, ADR 0020 §1.4). The presented token is loaded under a
 * pessimistic write lock so concurrent refreshes serialize. Replay of a revoked token triggers the
 * OQ-3 family revoke (RFC 6819) — a single bulk UPDATE revokes all still-active tokens of the user.
 * A LOCKED/DISABLED account cannot refresh (family revoked, then {@link InvalidTokenException}).
 */
@Component
class RefreshCommandHandler implements CommandHandler<RefreshCommand, RefreshCommand.Result> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AccessTokenCodec accessTokenCodec;
    private final IamSecurityParameters parameters;
    private final Clock clock;

    RefreshCommandHandler(RefreshTokenRepository refreshTokenRepository,
                          UserRepository userRepository,
                          AccessTokenCodec accessTokenCodec,
                          IamSecurityParameters parameters,
                          Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.accessTokenCodec = accessTokenCodec;
        this.parameters = parameters;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public RefreshCommand.Result handle(RefreshCommand command) {
        Instant now = Instant.now(clock);
        String tokenHash = TokenHash.sha256Hex(command.refreshToken());

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

        return new RefreshCommand.Result(accessToken, rawRefreshToken,
                Duration.ofMinutes(parameters.accessTtlMinutes()).toSeconds());
    }

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
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.isMustChangePassword(),
                now,
                now.plus(Duration.ofMinutes(accessTtlMinutes))
        );
    }
}
