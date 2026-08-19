package io.github.ryu200o.eduworkshop.iam.internal.application.event;

import io.github.ryu200o.eduworkshop.iam.contract.events.UserIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.contract.events.UserRegisteredIntegrationEvent;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserIntegrationEventPublisher;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserDomainEvent;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.event.UserRegistered;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to IAM domain events and maps them into cross-module integration events (contract level).
 * {@link UserRegistered} → {@link UserRegisteredIntegrationEvent} (account created); all other
 * domain events have no cross-module consumer yet and are skipped. The
 * {@link PasswordResetRequestedIntegrationEvent} has no matching domain event (token issuance does
 * not mutate the {@code User} aggregate) and is published directly by the forgot-password handler.
 * Runs {@code AFTER_COMMIT} in a new transaction, so a failed mapping never rolls back the business
 * transaction (ADR 0011 outbox guarantees durable delivery).
 */
@Component
class UserDomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserDomainEventListener.class);

    private final UserIntegrationEventPublisher publisher;

    UserDomainEventListener(UserIntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishIntegrationEvent(UserDomainEvent event) {
        UserIntegrationEvent integration = switch (event) {
            case UserRegistered e -> map(e);
            default -> null;
        };
        if (integration == null) {
            log.debug("IAM domain event {} has no integration event — skipping", event.getClass().getSimpleName());
            return;
        }
        log.debug("Publishing integration event: {}", integration);
        publisher.publish(integration);
    }

    private static UserRegisteredIntegrationEvent map(UserRegistered e) {
        return new UserRegisteredIntegrationEvent(
                e.userId().value(),
                e.email().value()
        );
    }
}