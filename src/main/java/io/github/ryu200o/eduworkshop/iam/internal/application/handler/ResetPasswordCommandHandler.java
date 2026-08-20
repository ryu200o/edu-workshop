package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ResetPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Consumes a one-time reset token and sets a new password. The token is loaded under a pessimistic
 * write lock and marked used in the same transaction (single-use, ADR 0015). Also revokes all
 * refresh tokens of the account (ADR 0020 §1.4: password reset invalidates the session family).
 */
@Component
class ResetPasswordCommandHandler implements CommandHandler<ResetPasswordCommand> {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    ResetPasswordCommandHandler(OneTimeTokenRepository oneTimeTokenRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                UserDomainEventPublisher userDomainEventPublisher,
                                PasswordEncoder passwordEncoder,
                                Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(ResetPasswordCommand command) {
        Instant now = Instant.now(clock);
        String tokenHash = TokenHash.sha256Hex(command.token());

        OneTimeToken token = oneTimeTokenRepository.loadByHashWithLock(tokenHash)
                .orElseThrow(InvalidTokenException::new);
        if (!token.isActive(now)) {
            throw new InvalidTokenException();
        }

        User user = userRepository.loadByIdWithLock(token.userId())
                .orElseThrow(() -> new UserNotFoundException(token.userId()));

        user.resetPassword(passwordEncoder.encode(command.newPassword()), now);
        userRepository.save(user);

        token.markUsed(now);
        oneTimeTokenRepository.save(token);

        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);

        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }
}
