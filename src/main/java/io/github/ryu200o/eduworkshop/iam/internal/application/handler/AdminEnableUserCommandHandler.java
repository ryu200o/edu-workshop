package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminEnableUserCommand;
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
 * Admin re-enable ({@code POST /iam/admin/users/{id}/enable}). Reverts a disabled account back to
 * {@code ACTIVE}. Idempotent per the aggregate. {@code UserNotFoundException} → 404.
 */
@Component
class AdminEnableUserCommandHandler
        implements CommandHandler<AdminEnableUserCommand, AdminEnableUserCommand.Result> {

    private final UserRepository userRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    AdminEnableUserCommandHandler(UserRepository userRepository,
                                  UserDomainEventPublisher userDomainEventPublisher,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminEnableUserCommand.Result handle(AdminEnableUserCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.enable(now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
        return new AdminEnableUserCommand.Result();
    }
}