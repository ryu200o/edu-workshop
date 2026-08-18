package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminUnlockUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUnlockUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private AdminUnlockUserCommandHandler handler() {
        return new AdminUnlockUserCommandHandler(userRepository, userDomainEventPublisher, clock);
    }

    private static User lockedUser(Instant now) {
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash",
                "Nguyen Van A", now);
        user.verifyEmail(now);
        user.lock(now);
        return user;
    }

    @Test
    void unlock_success_restoresActive() {
        User user = lockedUser(Instant.now(clock));
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        when(userRepository.loadByIdWithLock(user.getId())).thenReturn(Optional.of(user));

        handler().handle(new AdminUnlockUserCommand(user.getId().value()));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(userDomainEventPublisher).publish(anyList());
    }

    @Test
    void unlock_unknownUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.loadByIdWithLock(UserId.of(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new AdminUnlockUserCommand(userId)))
                .isInstanceOf(UserNotFoundException.class);
    }
}