package io.github.ryu200o.eduworkshop.room.internal.application.event;

import io.github.ryu200o.eduworkshop.room.contract.RoomCapacityChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRelocatedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomRenamedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateChangedIntegrationEvent;
import io.github.ryu200o.eduworkshop.room.contract.RoomStateContract;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomState;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCapacityChanged;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomCreated;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomDomainEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRelocatedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomRenamedEvent;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.event.RoomStateChanged;
import io.github.ryu200o.eduworkshop.shared.infrastructure.event.SpringDomainEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
class RoomIntegrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RoomIntegrationEventPublisher.class);

    private final SpringDomainEventPublisher publisher;

    RoomIntegrationEventPublisher(SpringDomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishIntegrationEvent(RoomDomainEvent event) {
        RoomIntegrationEvent integration = switch (event) {
            case RoomRenamedEvent e -> map(e);
            case RoomRelocatedEvent e -> map(e);
            case RoomCapacityChanged e -> map(e);
            case RoomStateChanged e -> map(e);
            case RoomCreated e -> null;
        };
        if (integration == null) {
            log.debug("RoomCreated domain event has no integration event — skipping");
            return;
        }
        log.debug("Publishing integration event: {}", integration);
        publisher.publishEvents(List.of(integration));
    }

    // -------------------------------------------------------------------------
    // Mapping: DomainEvent → IntegrationEvent
    // -------------------------------------------------------------------------

    private static RoomRenamedIntegrationEvent map(RoomRenamedEvent e) {
        return new RoomRenamedIntegrationEvent(
                e.roomId().value(),
                e.oldName().value(),
                e.newName().value(),
                e.occurredAt()
        );
    }

    private static RoomRelocatedIntegrationEvent map(RoomRelocatedEvent e) {
        return new RoomRelocatedIntegrationEvent(
                e.roomId().value(),
                e.oldLocation().asString(),
                e.newLocation().asString(),
                e.occurredAt()
        );
    }

    private static RoomCapacityChangedIntegrationEvent map(RoomCapacityChanged e) {
        return new RoomCapacityChangedIntegrationEvent(
                e.roomId().value(),
                e.oldCapacity().value(),
                e.newCapacity().value(),
                e.occurredAt()
        );
    }

    private static RoomStateChangedIntegrationEvent map(RoomStateChanged e) {
        return new RoomStateChangedIntegrationEvent(
                e.roomId().value(),
                toContract(e.previousState()),
                toContract(e.newState()),
                e.occurredAt()
        );
    }

    private static RoomStateContract toContract(RoomState state) {
        return switch (state) {
            case ACTIVE -> RoomStateContract.ACTIVE;
            case MAINTENANCE -> RoomStateContract.MAINTENANCE;
            case DEACTIVATED -> RoomStateContract.DEACTIVATED;
        };
    }
}
