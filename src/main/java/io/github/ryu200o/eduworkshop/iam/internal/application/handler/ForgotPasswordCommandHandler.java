package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.contract.events.PasswordResetRequestedIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ForgotPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserIntegrationEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Issues a one-time reset token for the forgot-password flow (plan §1.2 line 34). Answers
 * identically whether or not the email exists (no account enumeration): an unknown email yields a
 * {@code null} reset token, a known one the raw token — until SMTP integration the raw token is
 * returned through the response as a dev seam.
 */
@Component
class ForgotPasswordCommandHandler
        implements CommandHandler<ForgotPasswordCommand, ForgotPasswordCommand.Result> {

    private final UserRepository userRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserIntegrationEventPublisher userIntegrationEventPublisher;
    private final IamSecurityParameters parameters;
    private final Clock clock;

    ForgotPasswordCommandHandler(UserRepository userRepository,
                                 OneTimeTokenRepository oneTimeTokenRepository,
                                 UserIntegrationEventPublisher userIntegrationEventPublisher,
                                 IamSecurityParameters parameters,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.userIntegrationEventPublisher = userIntegrationEventPublisher;
        this.parameters = parameters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ForgotPasswordCommand.Result handle(ForgotPasswordCommand command) {
        Instant now = Instant.now(clock);
        Email email = Email.of(command.email());

        Optional<User> user = userRepository.loadByEmail(email);
        if (user.isEmpty()) {
            return new ForgotPasswordCommand.Result(null);
        }

        String rawToken = TokenHash.generateRaw();
        OneTimeToken resetToken = OneTimeToken.create(
                user.get().getId(),
                TokenHash.sha256Hex(rawToken),
                now.plus(Duration.ofHours(parameters.otpTtlHours())),
                now
        );
        oneTimeTokenRepository.save(resetToken);
        userIntegrationEventPublisher.publish(new PasswordResetRequestedIntegrationEvent(
                user.get().getId().value(),
                email.value(),
                resetToken.id()
        ));

        return new ForgotPasswordCommand.Result(rawToken);
    }
}
