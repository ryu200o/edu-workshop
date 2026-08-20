package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminUnlockUserCommand;
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
 * Admin unlock ({@code POST /iam/admin/users/{id}/unlock}). Lifts an explicit admin lock back to
 * {@code ACTIVE}; the cleared lock window is re-armed by the next login flow. Idempotent per the
 * aggregate. {@code UserNotFoundException} → 404.
 */
@Component
class AdminUnlockUserCommandHandler
        implements CommandHandler<AdminUnlockUserCommand> {

    private final UserRepository userRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    AdminUnlockUserCommandHandler(UserRepository userRepository,
                                  UserDomainEventPublisher userDomainEventPublisher,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(AdminUnlockUserCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.unlock(now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }
}