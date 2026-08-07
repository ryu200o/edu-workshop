package io.github.ryu200o.eduworkshop.shared.infrastructure.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomRenamedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.CreateRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.command.RenameRoomCommand;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.CommandBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.CreateWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.command.PlanWorkshopCommand;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the Spring Modulith Event Publication Registry (transactional
 * outbox) driving the real Room/Workshop event choreography. Assertions are behavior-first
 * through the Modulith beans ({@link CompletedEventPublications}); raw SQL is limited to the
 * {@code serialized_event} column.
 */
@SpringBootTest
class EventPublicationDurabilityTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private WorkshopRepository workshopRepository;

    @Autowired
    private CompletedEventPublications completed;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEventPublisher testEventPublisher;

    @Test
    void createRoom_recordsAndCompletesDomainEventPublication() {
        UUID roomId = createRoom("F-201");

        TargetEventPublication publication = single(RoomCreated.class, event -> event.roomId().value().equals(roomId));
        assertThat(publication.getTargetIdentifier().getValue()).contains("RoomDomainEventListener");
        assertThat(publication.isCompleted()).isTrue();
        assertThat(publication.getCompletionDate()).isPresent();
        assertThat(publication.getStatus()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void renameRoom_recordsBothDomainAndIntegrationEventPublications() {
        UUID roomId = createRoom("F-201");
        commandBus.execute(new RenameRoomCommand(roomId, "F-202"));

        TargetEventPublication domain = single(RoomRenamedEvent.class, event -> event.roomId().value().equals(roomId));
        assertThat(domain.getTargetIdentifier().getValue()).contains("RoomDomainEventListener");
        assertThat(((RoomRenamedEvent) domain.getEvent()).newName().value()).isEqualTo("F-202");

        TargetEventPublication integration = single(RoomRenamedIntegrationEvent.class, event -> event.roomId().equals(roomId));
        assertThat(integration.getTargetIdentifier().getValue()).contains("WorkshopRoomEventHandler");
        assertThat(((RoomRenamedIntegrationEvent) integration.getEvent()).newName()).isEqualTo("F-202");
    }

    @Test
    void eventMarkedCompletedAfterListenerSuccess() {
        UUID roomId = createRoom("F-201");
        commandBus.execute(new RenameRoomCommand(roomId, "F-203"));

        TargetEventPublication publication = single(RoomRenamedEvent.class, event -> event.roomId().value().equals(roomId));
        assertThat(publication.isCompleted()).isTrue();
        assertThat(publication.getCompletionDate()).isPresent();
        assertThat(publication.getStatus()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void serializedEventContainsExpectedBusinessPayload() {
        UUID roomId = createRoom("F-201");
        commandBus.execute(new RenameRoomCommand(roomId, "F-204"));

        String serialized = jdbcTemplate.queryForObject("""
                        SELECT serialized_event
                          FROM event_publication
                         WHERE event_type = ?
                           AND serialized_event LIKE ?
                        """,
                String.class, RoomRenamedIntegrationEvent.class.getName(), "%" + roomId + "%");

        assertThat(serialized)
                .contains("\"oldName\":\"F-201\"")
                .contains("\"newName\":\"F-204\"");
    }

    @Test
    void workshopSnapshotUpdatedAfterRename() {
        UUID roomId = createRoom("F-201");

        CreateWorkshopCommand.Result created = commandBus.execute(new CreateWorkshopCommand(
                "Intro to DDD", "workshop description",
                Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"), 25, null, null));

        commandBus.execute(new PlanWorkshopCommand(created.id(), roomId));

        commandBus.execute(new RenameRoomCommand(roomId, "F-205"));

        Workshop workshop = workshopRepository.loadById(WorkshopId.of(created.id())).orElseThrow();
        assertThat(workshop.roomReference()).isNotNull();
        assertThat(workshop.roomReference().roomNameSnapshot()).isEqualTo("F-205");
    }

    @Test
    void noRowWhenPublisherTransactionRollsBack() {
        TestEvent rolledBack = new TestEvent(UUID.randomUUID(), "rolled back");

        assertThatThrownBy(() -> testEventPublisher.publishAndRollback(rolledBack))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(rolledBack.id().toString());

        assertThat(findAll(TestEvent.class, event -> event.id().equals(rolledBack.id()))).isEmpty();
    }

    private UUID createRoom(String name) {
        int n = SEQUENCE.incrementAndGet();
        return commandBus.execute(new CreateRoomCommand("B" + n, 2, n, name, 50)).id();
    }

    private <T> List<TargetEventPublication> findAll(Class<T> eventType, Predicate<T> filter) {
        return completed.findAll().stream()
                .filter(publication -> eventType.isInstance(publication.getEvent()))
                .filter(publication -> filter.test(eventType.cast(publication.getEvent())))
                .map(publication -> (TargetEventPublication) publication)
                .toList();
    }

    private <T> TargetEventPublication single(Class<T> eventType, Predicate<T> filter) {
        return findAll(eventType, filter).stream().findFirst().orElseThrow();
    }
}
