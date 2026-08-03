package io.github.ryu200o.eduworkshop.workshop.internal.application.event;

import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopCancelledIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopIntegrationEvent;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopIntegrationEventPublisher;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCancelled;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.event.WorkshopCreated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkshopDomainEventListenerTest {

    private static final WorkshopId WORKSHOP_ID = WorkshopId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Mock
    private WorkshopIntegrationEventPublisher publisher;

    @Test
    void workshopCancelled_mapsToIntegrationEvent() {
        WorkshopDomainEventListener listener = new WorkshopDomainEventListener(publisher);
        WorkshopCancelled domainEvent = new WorkshopCancelled(WORKSHOP_ID, NOW);

        listener.publishIntegrationEvent(domainEvent);

        ArgumentCaptor<WorkshopIntegrationEvent> captor = ArgumentCaptor.forClass(WorkshopIntegrationEvent.class);
        verify(publisher).publish(captor.capture());

        assertThat(captor.getValue())
                .isInstanceOf(WorkshopCancelledIntegrationEvent.class)
                .satisfies(e -> {
                    WorkshopCancelledIntegrationEvent integration = (WorkshopCancelledIntegrationEvent) e;
                    assertThat(integration.workshopId()).isEqualTo(WORKSHOP_ID.value());
                    assertThat(integration.occurredAt()).isEqualTo(NOW);
                });
    }

    @Test
    void domainEventWithoutConsumer_isSkipped() {
        WorkshopDomainEventListener listener = new WorkshopDomainEventListener(publisher);

        listener.publishIntegrationEvent(new WorkshopCreated(
                WORKSHOP_ID.value(), WORKSHOP_ID,
                NOW, NOW.plusSeconds(7200), null, NOW));

        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
