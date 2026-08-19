package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.UpdateProfileCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProfileCommandHandlerTest {

    @Mock
    private UserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private UpdateProfileCommandHandler handler() {
        return new UpdateProfileCommandHandler(userRepository, clock);
    }

    private static User activeUser(Instant now) {
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$hash",
                "Nguyen Van A", now);
        user.verifyEmail(now);
        return user;
    }

    @Test
    void updateProfile_success_updatesEditableFields_andSaves() {
        User user = activeUser(Instant.now(clock));
        when(userRepository.loadById(user.getId())).thenReturn(Optional.of(user));

        handler().handle(new UpdateProfileCommand(user.getId().value(), "Tran Thi B", "0901234567",
                "B21DCVT000", "https://cdn.example.com/avatar.png"));

        assertThat(user.getFullName()).isEqualTo("Tran Thi B");
        assertThat(user.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(user.getStudentCode()).isEqualTo("B21DCVT000");
        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_blankFullName_throwsIllegalArgument() {
        User user = activeUser(Instant.now(clock));
        when(userRepository.loadById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> handler().handle(new UpdateProfileCommand(
                user.getId().value(), " ", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_unknownUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.loadById(UserId.of(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new UpdateProfileCommand(
                userId, "X", null, null, null)))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }
}