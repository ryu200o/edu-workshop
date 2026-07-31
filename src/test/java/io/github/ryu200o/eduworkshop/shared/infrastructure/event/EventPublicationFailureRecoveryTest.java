package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.EventPublicationRegistry;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure and recovery integration tests for the Event Publication Registry: a failing
 * listener leaves the publication uncompleted, and resubmission re-delivers the event
 * (at-least-once) until the listener succeeds.
 */
@SpringBootTest
class EventPublicationFailureRecoveryTest {

    @Autowired
    private TestEventPublisher testEventPublisher;

    @Autowired
    private EventPublicationRegistry registry;

    @Autowired
    private CompletedEventPublications completed;

    @Autowired
    private FailedEventPublications failed;

    @AfterEach
    void resetTestListener() {
        TestEventListener.reset();
    }

    @Test
    void eventRemainsUncompletedWhenListenerFails() {
        UUID eventId = UUID.randomUUID();
        testEventPublisher.publish(new TestEvent(eventId, "boom"));

        List<EventPublication> incomplete = registry.findIncompletePublications().stream()
                .filter(publication -> publication.getEvent() instanceof TestEvent event
                        && event.id().equals(eventId))
                .map(publication -> (EventPublication) publication)
                .toList();

        assertThat(incomplete).hasSize(1);
        assertThat(incomplete.get(0).getStatus()).isEqualTo(Status.FAILED);
        assertThat(incomplete.get(0).getCompletionDate()).isEmpty();
        assertThat(TestEventListener.attemptsFor(eventId)).isEqualTo(1);
    }

    @Test
    void resubmitDeliversAgainAndCompletes() {
        UUID eventId = UUID.randomUUID();
        testEventPublisher.publish(new TestEvent(eventId, "retry"));

        assertThat(TestEventListener.attemptsFor(eventId)).isEqualTo(1);
        assertThat(completed.findAll()).noneMatch(publication ->
                publication.getEvent() instanceof TestEvent event && event.id().equals(eventId));

        failed.resubmit(ResubmissionOptions.defaults());

        assertThat(TestEventListener.attemptsFor(eventId)).isEqualTo(2);

        EventPublication done = completed.findAll().stream()
                .filter(publication -> publication.getEvent() instanceof TestEvent event
                        && event.id().equals(eventId))
                .map(publication -> (EventPublication) publication)
                .findFirst()
                .orElseThrow();

        assertThat(done.isCompleted()).isTrue();
        assertThat(done.getCompletionDate()).isPresent();
        assertThat(done.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(registry.findIncompletePublications()).noneMatch(publication ->
                publication.getEvent() instanceof TestEvent event && event.id().equals(eventId));
    }
}
