package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes {@link TestEvent} inside its own {@code REQUIRES_NEW} transaction so the
 * AFTER_COMMIT transactional listener runs synchronously before the method returns.
 * A failing listener surfaces as an exception on commit; it is swallowed here because
 * the event publication state in the registry (FAILED) is the authoritative signal the
 * tests assert on.
 */
@Component
class TestEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    TestEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publish(TestEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception o_O) {
            // The outbox row is already committed (with FAILED status) before the listener
            // exception propagates on commit — tests assert on the registry, not on this.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishAndRollback(TestEvent event) {
        applicationEventPublisher.publishEvent(event);
        throw new IllegalStateException("Forced rollback of " + event.id());
    }
}
