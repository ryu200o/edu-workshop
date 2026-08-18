package io.github.ryu200o.eduworkshop.iam.internal.application.event;

import io.github.ryu200o.eduworkshop.iam.contract.events.PasswordResetRequestedIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.contract.events.UserRegisteredIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.ForgotPasswordCommand;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.command.RegisterCommand;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserRegistered;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests proving the IAM integration events flow through the Spring Modulith Event
 * Publication Registry (transactional outbox, ADR 0011): {@link UserRegisteredIntegrationEvent}
 * (mapped from the {@code UserRegistered} domain event by {@link UserDomainEventListener}) and
 * {@link PasswordResetRequestedIntegrationEvent} (published directly by the forgot-password handler).
 * Assertions are behavior-first through {@link CompletedEventPublications}. The test-only
 * {@link TestIamIntegrationEventListener} is the matching target that lets Modulith record +
 * complete the publications — there is no business consumer for these events yet.
 */
@SpringBootTest
class IamEventPublicationDurabilityTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private CompletedEventPublications completed;

    @Test
    void registerUser_recordsAndCompletesDomainAndIntegrationEventPublications() {
        RegisterCommand.Result result = commandBus.execute(
                new RegisterCommand("durability-student@example.com", "Passw0rd!", "Nguyen Van A"));

        TargetEventPublication domain = single(UserRegistered.class, e -> e.userId().value().equals(result.userId()));
        assertThat(domain.getTargetIdentifier().getValue()).contains("UserDomainEventListener");
        assertThat(domain.isCompleted()).isTrue();

        TargetEventPublication integration = single(UserRegisteredIntegrationEvent.class,
                e -> e.userId().equals(result.userId()));
        assertThat(integration.getTargetIdentifier().getValue()).contains("TestIamIntegrationEventListener");
        assertThat(integration.isCompleted()).isTrue();
        assertThat(integration.getCompletionDate()).isPresent();
        assertThat(integration.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(((UserRegisteredIntegrationEvent) integration.getEvent()).email())
                .isEqualTo("durability-student@example.com");
    }

    @Test
    void forgotPassword_recordsAndCompletesResetRequestIntegrationEventPublication() {
        RegisterCommand.Result result = commandBus.execute(
                new RegisterCommand("durability-reset@example.com", "Passw0rd!", "Nguyen Van A"));
        commandBus.execute(new ForgotPasswordCommand("durability-reset@example.com"));

        TargetEventPublication integration = single(PasswordResetRequestedIntegrationEvent.class,
                e -> e.userId().equals(result.userId()));
        assertThat(integration.getTargetIdentifier().getValue()).contains("TestIamIntegrationEventListener");
        assertThat(integration.isCompleted()).isTrue();
        assertThat(integration.getCompletionDate()).isPresent();
        assertThat(integration.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(((PasswordResetRequestedIntegrationEvent) integration.getEvent()).email())
                .isEqualTo("durability-reset@example.com");
        assertThat(((PasswordResetRequestedIntegrationEvent) integration.getEvent()).tokenId())
                .isNotNull();
    }

    private <T> TargetEventPublication single(Class<T> eventType, Predicate<T> filter) {
        return completed.findAll().stream()
                .filter(publication -> eventType.isInstance(publication.getEvent()))
                .filter(publication -> filter.test(eventType.cast(publication.getEvent())))
                .map(publication -> (TargetEventPublication) publication)
                .findFirst()
                .orElseThrow();
    }
}