package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.AdminCreateUserCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.GlobalRole;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCreateUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

    private AdminCreateUserCommandHandler handler() {
        return new AdminCreateUserCommandHandler(userRepository, userDomainEventPublisher,
                passwordEncoder, clock);
    }

    @Test
    void createUser_success_accountIsActiveWithMcpAndBaseRole() {
        when(userRepository.existsByEmail(Email.of("new@example.com"))).thenReturn(false);
        when(passwordEncoder.encode("Temporary!1")).thenReturn("$2a$12$newHash");

        AdminCreateUserCommand.Result result = handler().handle(new AdminCreateUserCommand(
                "new@example.com", "Nguyen Van B", "Temporary!1", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(result.userId()).isEqualTo(saved.getId().value());
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.getEmail().value()).isEqualTo("new@example.com");
        assertThat(saved.getRoles()).containsExactly(GlobalRole.USER);
        verify(userDomainEventPublisher).publish(anyList());
    }

    @Test
    void createUser_duplicateEmail_throwsDuplicate() {
        when(userRepository.existsByEmail(Email.of("dup@example.com"))).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(new AdminCreateUserCommand(
                "dup@example.com", "Dup", "Temporary!1", null)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_extraRoles_areAppliedOnTopOfBaseRole() {
        when(userRepository.existsByEmail(Email.of("planner@example.com"))).thenReturn(false);
        when(passwordEncoder.encode("Temporary!1")).thenReturn("$2a$12$newHash");

        handler().handle(new AdminCreateUserCommand("planner@example.com", "Planner", "Temporary!1",
                Set.of("USER", "PLANNER")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactlyInAnyOrder(GlobalRole.USER, GlobalRole.PLANNER);
    }

    @Test
    void createUser_rolesWithoutBaseRole_throwsIllegalArgument() {
        when(userRepository.existsByEmail(Email.of("bad@example.com"))).thenReturn(false);
        when(passwordEncoder.encode("Temporary!1")).thenReturn("$2a$12$newHash");

        assertThatThrownBy(() -> handler().handle(new AdminCreateUserCommand(
                "bad@example.com", "Bad", "Temporary!1", Set.of("ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_blankTemporaryPassword_throwsIllegalArgument() {
        when(userRepository.existsByEmail(Email.of("blank@example.com"))).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(new AdminCreateUserCommand(
                "blank@example.com", "Blank", "  ", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }
}