package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restart-replay integration test. Phase 1 publishes an event whose listener fails once,
 * leaving the publication FAILED/uncompleted. Phase 2 boots a fresh Spring context with
 * {@code spring.modulith.events.republish-outstanding-events-on-restart=true}, so the
 * outstanding publication is re-delivered on startup and completed (attempt #2 succeeds).
 *
 * <p>The listener's attempt counter is static, so it survives the context restart in the
 * same JVM. Each phase gets a fresh context via {@link DirtiesContext}; the in-memory H2
 * database (DB_CLOSE_DELAY=-1) keeps the publication row across the restart.</p>
 */
@SpringBootTest(properties = "spring.modulith.events.republish-outstanding-events-on-restart=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventPublicationRestartReplayTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired
    private TestEventPublisher testEventPublisher;

    @Autowired
    private CompletedEventPublications completed;

    @Test
    @Order(1)
    void leavesPublicationIncompleteBeforeRestart() {
        testEventPublisher.publish(new TestEvent(EVENT_ID, "survives restart"));

        assertThat(TestEventListener.attemptsFor(EVENT_ID)).isEqualTo(1);
        assertThat(completed.findAll()).noneMatch(publication ->
                publication.getEvent() instanceof TestEvent event && event.id().equals(EVENT_ID));
    }

    @Test
    @Order(2)
    void replaysOutstandingPublicationOnRestart() {
        EventPublication publication = completed.findAll().stream()
                .filter(p -> p.getEvent() instanceof TestEvent event && event.id().equals(EVENT_ID))
                .map(p -> (EventPublication) p)
                .findFirst()
                .orElseThrow();

        assertThat(publication.isCompleted()).isTrue();
        assertThat(publication.getCompletionDate()).isPresent();
        assertThat(TestEventListener.attemptsFor(EVENT_ID)).isEqualTo(2);
    }
}
