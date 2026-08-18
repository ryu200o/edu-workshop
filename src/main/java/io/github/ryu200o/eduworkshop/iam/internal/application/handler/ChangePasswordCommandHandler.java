package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidCredentialsException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ChangePasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Self-service password change ({@code POST /iam/me/change-password}). Proves knowledge of the
 * current password first (wrong {@code currentPassword} → 401, no state change), then delegates to
 * the aggregate's {@code changePassword} (requires {@code ACTIVE}; clears the mcp gate) and kills
 * every active session of the user (RFC 6819 family protection). The change is immediately effective,
 * so the current password stops working on all devices.
 */
@Component
class ChangePasswordCommandHandler implements CommandHandler<ChangePasswordCommand, ChangePasswordCommand.Result> {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    ChangePasswordCommandHandler(UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 UserDomainEventPublisher userDomainEventPublisher,
                                 PasswordEncoder passwordEncoder,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChangePasswordCommand.Result handle(ChangePasswordCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (command.newPassword() == null || command.newPassword().isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }

        user.changePassword(passwordEncoder.encode(command.newPassword()), now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
        return new ChangePasswordCommand.Result();
    }
}