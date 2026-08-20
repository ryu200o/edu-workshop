package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminCreateUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin create-user ({@code POST /iam/admin/users}, OQ-4). Orchestrates the global email-uniqueness
 * rule per ADR 0005 (Revised): fast-fail via {@code existsByEmail} → {@link DuplicateEmailException},
 * with the DB unique index as the race-proof backstop. The account is born {@code ACTIVE} with
 * {@code must_change_password = true} (48h temp-password TTL deferred, plan §8 risk #6). Extra roles
 * are applied through the aggregate's {@code updateRoles}, which enforces the base {@code USER} role.
 */
@Component
class AdminCreateUserCommandHandler implements CommandHandler<AdminCreateUserCommand> {

    private final UserRepository userRepository;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AdminCreateUserCommandHandler(UserRepository userRepository,
                                  UserDomainEventPublisher userDomainEventPublisher,
                                  PasswordEncoder passwordEncoder,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(AdminCreateUserCommand command) {
        Instant now = Instant.now(clock);
        Email email = Email.of(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        if (command.temporaryPassword() == null || command.temporaryPassword().isBlank()) {
            throw new IllegalArgumentException("temporaryPassword must not be blank");
        }

        User user = User.createByAdmin(UserId.of(command.userId()), email,
                passwordEncoder.encode(command.temporaryPassword()), command.fullName(), now);
        if (command.roles() != null && !command.roles().isEmpty()) {
            user.updateRoles(parseRoles(command.roles()), now);
        }

        userRepository.save(user);
        userDomainEventPublisher.publish(user.recordedEvents());
        user.clearRecordedEvents();
    }

    private static Set<GlobalRole> parseRoles(Set<String> roleNames) {
        return roleNames.stream().map(GlobalRole::valueOf).collect(Collectors.toSet());
    }
}