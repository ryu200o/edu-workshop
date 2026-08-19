package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RegisterCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Orchestrates public self-registration (plan §2.1 steps 1-3): checks email uniqueness (fast-fail,
 * ADR 0005; the DB unique index is the race-proof backstop), creates the {@code PENDING_VERIFICATION}
 * aggregate, persists it, and mints a one-time verify token whose hash is persisted (ADR 0021 — the
 * raw value is never returned over HTTP; delivery moves to the notification/outbox channel).
 */
@Component
class RegisterCommandHandler implements CommandHandler<RegisterCommand> {

    private final UserRepository userRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final IamSecurityParameters parameters;
    private final Clock clock;

    RegisterCommandHandler(UserRepository userRepository,
                           OneTimeTokenRepository oneTimeTokenRepository,
                           UserDomainEventPublisher userDomainEventPublisher,
                           PasswordEncoder passwordEncoder,
                           IamSecurityParameters parameters,
                           Clock clock) {
        this.userRepository = userRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.parameters = parameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(RegisterCommand command) {
        Email email = Email.of(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Instant now = Instant.now(clock);
        String passwordHash = passwordEncoder.encode(command.password());
        User user = User.create(UserId.of(command.userId()), email, passwordHash, command.fullName(), now);
        userRepository.save(user);

        String rawToken = TokenHash.generateRaw();
        OneTimeToken verifyToken = OneTimeToken.create(
                user.getId(),
                TokenHash.sha256Hex(rawToken),
                now.plus(Duration.ofHours(parameters.otpTtlHours())),
                now
        );
        oneTimeTokenRepository.save(verifyToken);

        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }
}
