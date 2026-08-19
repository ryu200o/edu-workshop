package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminUpdateRolesCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin role replacement ({@code PUT /iam/admin/users/{id}/roles}). Loads the aggregate under a
 * pessimistic write lock (ADR 0015), delegates the role-set invariant (base {@code USER} always
 * present) to {@code updateRoles}, and publishes the {@code RolesUpdated} event. Invalid role name or
 * a set omitting {@code USER} → 400.
 */
@Component
class AdminUpdateRolesCommandHandler
        implements CommandHandler<AdminUpdateRolesCommand> {

    private final UserRepository userRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final Clock clock;

    AdminUpdateRolesCommandHandler(UserRepository userRepository,
                                   UserDomainEventPublisher userDomainEventPublisher,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(AdminUpdateRolesCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.updateRoles(parseRoles(command.roles()), now);

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }

    private static Set<GlobalRole> parseRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        return roleNames.stream().map(GlobalRole::valueOf).collect(Collectors.toSet());
    }
}