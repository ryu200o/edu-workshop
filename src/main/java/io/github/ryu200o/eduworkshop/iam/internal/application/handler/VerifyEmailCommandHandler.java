package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.VerifyEmailCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Consumes a one-time verify-email token (plan §2.1 step 4). The token is loaded under a pessimistic
 * write lock and marked used in the same transaction, so a single-use token cannot be replayed
 * (ADR 0015). An unknown, used, or expired token raises {@link InvalidTokenException}.
 */
@Component
class VerifyEmailCommandHandler implements CommandHandler<VerifyEmailCommand> {

    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    VerifyEmailCommandHandler(OneTimeTokenRepository oneTimeTokenRepository,
                              UserRepository userRepository,
                              UserDomainEventPublisher userDomainEventPublisher,
                              Clock clock) {
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userRepository = userRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(VerifyEmailCommand command) {
        Instant now = Instant.now(clock);
        String tokenHash = TokenHash.sha256Hex(command.token());

        OneTimeToken token = oneTimeTokenRepository.loadByHashWithLock(tokenHash)
                .orElseThrow(InvalidTokenException::new);
        if (!token.isActive(now)) {
            throw new InvalidTokenException();
        }

        User user = userRepository.loadByIdWithLock(token.userId())
                .orElseThrow(() -> new UserNotFoundException(token.userId()));
        user.verifyEmail(now);
        userRepository.save(user);

        token.markUsed(now);
        oneTimeTokenRepository.save(token);

        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }
}
