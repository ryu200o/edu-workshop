package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidCredentialsException;
import io.github.ryu200o.eduworkshop.iam.internal.application.exception.UserNotFoundException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ChangePasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.RefreshTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private ChangePasswordCommandHandler handler() {
        return new ChangePasswordCommandHandler(userRepository, refreshTokenRepository,
                userDomainEventPublisher, passwordEncoder, clock);
    }

    private static User activeUser(Instant now) {
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "$2a$12$oldHash",
                "Nguyen Van A", now);
        user.verifyEmail(now);
        return user;
    }

    @Test
    void changePassword_wrongCurrentPassword_rejectsWithoutStateChange() {
        User user = activeUser(Instant.now(clock));
        when(userRepository.loadById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$12$oldHash")).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(new ChangePasswordCommand(
                user.getId().value(), "wrong", "NewPassw0rd!")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$oldHash");
        verify(userRepository, never()).save(user);
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        verify(userDomainEventPublisher, never()).publish(anyList());
    }

    @Test
    void changePassword_success_clearsMcp_andRevokesAllSessions() {
        User user = activeUser(Instant.now(clock));
        user.resetPassword("$2a$12$oldHash", Instant.now(clock));
        assertThat(user.isMustChangePassword()).isTrue();
        when(userRepository.loadById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Temporary!1", "$2a$12$oldHash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("$2a$12$newHash");

        handler().handle(new ChangePasswordCommand(
                user.getId().value(), "Temporary!1", "NewPassw0rd!"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHash");
        assertThat(user.isMustChangePassword()).isFalse();
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(user.getId()), any());
        verify(userRepository).save(user);
        verify(userDomainEventPublisher).publish(anyList());
    }

    @Test
    void changePassword_blankNewPassword_throwsIllegalArgument() {
        User user = activeUser(Instant.now(clock));
        when(userRepository.loadById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$2a$12$oldHash")).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new ChangePasswordCommand(
                user.getId().value(), "current", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(user);
    }

    @Test
    void changePassword_unknownUser_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.loadById(UserId.of(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(new ChangePasswordCommand(
                userId, "current", "NewPassw0rd!")))
                .isInstanceOf(UserNotFoundException.class);
    }
}