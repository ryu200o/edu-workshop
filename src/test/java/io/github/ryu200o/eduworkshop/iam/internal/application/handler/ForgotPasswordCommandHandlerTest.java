package io.github.ryu200o.eduworkshop.iam.internal.application.handler;

import io.github.ryu200o.eduworkshop.iam.contract.events.PasswordResetRequestedIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ForgotPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.OneTimeTokenRepository;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserIntegrationEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserRepository;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.Email;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.OneTimeToken;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.User;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Mock
    private UserIntegrationEventPublisher userIntegrationEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);
    private final IamSecurityParameters parameters = new IamSecurityParameters("secret", 15, 7, 24, true);

    private ForgotPasswordCommandHandler handler() {
        return new ForgotPasswordCommandHandler(
                userRepository, oneTimeTokenRepository, userIntegrationEventPublisher, parameters, clock);
    }

    @Test
    void forgotPassword_knownEmail_issuesResetTokenAndPublishesIntegrationEvent() {
        Instant now = Instant.now(clock);
        User user = User.create(UserId.generate(), Email.of("student@example.com"), "hash", "A", now);
        when(userRepository.loadByEmail(Email.of("student@example.com"))).thenReturn(Optional.of(user));

        ForgotPasswordCommand.Result result = handler().handle(new ForgotPasswordCommand("student@example.com"));

        assertThat(result.resetToken()).isNotBlank();

        ArgumentCaptor<OneTimeToken> tokenCaptor = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(oneTimeTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().tokenHash()).isEqualTo(TokenHash.sha256Hex(result.resetToken()));
        assertThat(tokenCaptor.getValue().userId()).isEqualTo(user.getId());

        ArgumentCaptor<PasswordResetRequestedIntegrationEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetRequestedIntegrationEvent.class);
        verify(userIntegrationEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(user.getId().value());
        assertThat(eventCaptor.getValue().email()).isEqualTo("student@example.com");
        assertThat(eventCaptor.getValue().tokenId()).isEqualTo(tokenCaptor.getValue().id());
    }

    @Test
    void forgotPassword_unknownEmail_returnsNullTokenSilentlyAndPublishesNothing() {
        when(userRepository.loadByEmail(Email.of("nobody@example.com"))).thenReturn(Optional.empty());

        ForgotPasswordCommand.Result result = handler().handle(new ForgotPasswordCommand("nobody@example.com"));

        assertThat(result.resetToken()).isNull();
        verify(oneTimeTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userIntegrationEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void forgotPassword_normalizesEmailBeforeLookup() {
        when(userRepository.loadByEmail(Email.of("mixed@case.com"))).thenReturn(Optional.empty());

        handler().handle(new ForgotPasswordCommand("Mixed@Case.com"));

        verify(userRepository).loadByEmail(Email.of("mixed@case.com"));
    }
}
