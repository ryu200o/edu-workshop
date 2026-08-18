package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.UpdateProfileCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandHandler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Self-service profile update ({@code PUT /iam/me/profile}, OQ-5). The controller has already
 * rejected payloads that carry {@code email}/{@code password}; the handler delegates the remaining
 * invariant (ACTIVE state, non-blank {@code fullName}) to the aggregate's {@code updateProfile}.
 * Profile changes are silent — the domain records no event (ADR 0020: profile is not security state).
 */
@Component
class UpdateProfileCommandHandler implements CommandHandler<UpdateProfileCommand, UpdateProfileCommand.Result> {

    private final UserRepository userRepository;
    private final Clock clock;

    UpdateProfileCommandHandler(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UpdateProfileCommand.Result handle(UpdateProfileCommand command) {
        Instant now = Instant.now(clock);
        UserId userId = UserId.of(command.userId());
        User user = userRepository.loadById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.updateProfile(command.fullName(), command.phoneNumber(),
                command.studentCode(), command.avatarUrl(), now);
        userRepository.save(user);
        return new UpdateProfileCommand.Result();
    }
}