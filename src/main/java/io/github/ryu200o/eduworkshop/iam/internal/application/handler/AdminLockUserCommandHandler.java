package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminLockUserCommand;
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
 * Admin lock ({@code POST /iam/admin/users/{id}/lock}). Explicit infinite lockout plus immediate
 * revocation of every active refresh token (RFC 6819 family protection). Idempotent per the
 * aggregate. {@code UserNotFoundException} → 404.
 */
@Component
class AdminLockUserCommandHandler implements CommandHandler<AdminLockUserCommand> {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    AdminLockUserCommandHandler(UserRepository userRepository,
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
    public void handle(AdminLockUserCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.lock(now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }
}