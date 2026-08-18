package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminDisableUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Admin disable ({@code POST /iam/admin/users/{id}/disable}). Disables the account (only
 * {@code enable} reverts it) and revokes every active refresh token. Idempotent per the aggregate.
 * {@code UserNotFoundException} → 404.
 */
@Component
class AdminDisableUserCommandHandler
        implements CommandHandler<AdminDisableUserCommand, AdminDisableUserCommand.Result> {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    AdminDisableUserCommandHandler(UserRepository userRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   UserDomainEventPublisher userDomainEventPublisher,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminDisableUserCommand.Result handle(AdminDisableUserCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.disable(now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
        return new AdminDisableUserCommand.Result();
    }
}