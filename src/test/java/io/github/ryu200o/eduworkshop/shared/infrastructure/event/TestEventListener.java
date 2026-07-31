package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only transactional event listener. The first delivery of each distinct
 * {@link TestEvent} (identified by {@link TestEvent#id()}) throws; every later
 * delivery succeeds. Static state survives Spring context restarts within the same
 * JVM, which is exactly what the restart-replay test relies on.
 */
@Component
class TestEventListener {

    private static final Map<UUID, Integer> ATTEMPTS = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL_INVOCATIONS = new AtomicInteger();

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void handle(TestEvent event) {
        TOTAL_INVOCATIONS.incrementAndGet();
        int attempt = ATTEMPTS.merge(event.id(), 1, Integer::sum);
        if (attempt == 1) {
            throw new IllegalStateException("Simulated listener failure for " + event.id());
        }
    }

    static int attemptsFor(UUID eventId) {
        return ATTEMPTS.getOrDefault(eventId, 0);
    }

    static int totalInvocations() {
        return TOTAL_INVOCATIONS.get();
    }

    static void reset() {
        ATTEMPTS.clear();
        TOTAL_INVOCATIONS.set(0);
    }
}
