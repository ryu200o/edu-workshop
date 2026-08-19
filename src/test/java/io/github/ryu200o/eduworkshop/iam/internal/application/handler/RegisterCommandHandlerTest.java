package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.DuplicateEmailException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RegisterCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserDomainEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserStatus;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserRegistered;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
    private final IamSecurityParameters parameters = new IamSecurityParameters("secret", 15, 7, 24, true);

    private RegisterCommandHandler handler() {
        return new RegisterCommandHandler(userRepository, oneTimeTokenRepository,
                userDomainEventPublisher, passwordEncoder, parameters, clock);
    }

    @Test
    void duplicateEmail_isRejectedBeforeAggregateCreation() {
        when(userRepository.existsByEmail(Email.of("student@example.com"))).thenReturn(true);

        assertThatThrownBy(() -> handler().handle(
                new RegisterCommand("student@example.com", "Passw0rd!", "Nguyen Van A")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(oneTimeTokenRepository, never()).save(any());
    }

    @Test
    void register_createsPendingUser_withVerifyToken() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$12$encodedHash");

        RegisterCommand.Result result = handler().handle(
                new RegisterCommand("Student@Example.com", "Passw0rd!", "Nguyen Van A"));

        assertThat(result.verifyToken()).isNotBlank();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail().value()).isEqualTo("student@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$encodedHash");
        assertThat(result.userId()).isEqualTo(saved.getId().value());

        ArgumentCaptor<OneTimeToken> tokenCaptor = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(oneTimeTokenRepository).save(tokenCaptor.capture());
        OneTimeToken token = tokenCaptor.getValue();
        assertThat(token.userId()).isEqualTo(saved.getId());
        assertThat(token.tokenHash()).isEqualTo(TokenHash.sha256Hex(result.verifyToken()));
        assertThat(token.isActive(Instant.now(clock))).isTrue();

        verify(userDomainEventPublisher).publish(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void register_normalizesEmailToLowercase() {
        when(userRepository.existsByEmail(Email.of("Mixed@Case.COM"))).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        handler().handle(new RegisterCommand("Mixed@Case.COM", "Passw0rd!", "A"));

        org.mockito.Mockito.verify(userRepository).existsByEmail(Email.of("mixed@case.com"));
    }

    @Test
    void register_rejectsMalformedEmail() {
        assertThatThrownBy(() -> handler().handle(new RegisterCommand("not-an-email", "x", "A")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }
}
