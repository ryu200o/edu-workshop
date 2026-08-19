package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminResetPasswordCommand;
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
 * Admin password reset ({@code POST /iam/admin/users/{id}/reset-password}). Sets a new password in
 * any account state, forces the mcp gate on, and revokes every active refresh token (old sessions
 * die immediately). Blank {@code newPassword} → 400.
 */
@Component
class AdminResetPasswordCommandHandler
        implements CommandHandler<AdminResetPasswordCommand, AdminResetPasswordCommand.Result> {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AdminResetPasswordCommandHandler(UserRepository userRepository,
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
    public AdminResetPasswordCommand.Result handle(AdminResetPasswordCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (command.newPassword() == null || command.newPassword().isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }

        user.resetPassword(passwordEncoder.encode(command.newPassword()), now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
        return new AdminResetPasswordCommand.Result();
    }
}