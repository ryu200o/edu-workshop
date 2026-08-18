package io.github.ryu200o.eduworkshop.iam.internal.application.event;

import io.github.ryu200o.eduworkshop.iam.contract.events.UserIntegrationEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Test-only transactional event listener for the IAM integration events ({@link UserIntegrationEvent}).
 * There is no business consumer for these events yet (the notification module is future work), but
 * Spring Modulith's publication registry only records an {@code event_publication} row when a
 * matching listener exists — this no-op listener is that target, so the outbox durability test can
 * assert the publication is persisted and completed (ADR 0011).
 */
@Component
class TestIamIntegrationEventListener {

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void handle(UserIntegrationEvent event) {
        // Intentionally a no-op consumer.
    }
}