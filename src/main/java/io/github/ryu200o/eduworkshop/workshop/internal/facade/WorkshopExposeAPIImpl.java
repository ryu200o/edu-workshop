package io.github.ryu200o.eduworkshop.workshop.internal.facade;

import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopImpactContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Package-private implementation of {@link WorkshopExposeAPI} — the Module Facade for Workshop.
 * Resides inside the information-hiding boundary (internal/facade/). Coordinates directly
 * with application ports — no Command/Query Bus involved (per ADR 0010).
 */
@Component
class WorkshopExposeAPIImpl implements WorkshopExposeAPI {

    private final WorkshopReader workshopReader;
    private final WorkshopRepository workshopRepository;

    WorkshopExposeAPIImpl(WorkshopReader workshopReader, WorkshopRepository workshopRepository) {
        this.workshopReader = workshopReader;
        this.workshopRepository = workshopRepository;
    }

    @Override
    public Optional<WorkshopRegistrationContract> lockForRegistration(UUID workshopId) {
        return workshopRepository.loadByIdWithLock(WorkshopId.of(workshopId))
                .map(this::toRegistrationContract);
    }

    private WorkshopRegistrationContract toRegistrationContract(Workshop workshop) {
        return new WorkshopRegistrationContract(
                workshop.id().value(),
                mapState(workshop.state()),
                workshop.startTime(),
                workshop.capacity().value(),
                workshop.title().value(),
                workshop.endTime(),
                workshop.roomReference() != null ? workshop.roomReference().roomNameSnapshot() : null);
    }

    @Override
    public List<WorkshopImpactContract> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime) {
        return workshopReader.getByRoomAndTimeOverlap(roomId, startTime, endTime).stream()
                .map(view -> new WorkshopImpactContract(
                        view.id(),
                        mapState(view.state())))
                .toList();
    }

    private static WorkshopStateContract mapState(String state) {
        return switch (state) {
            case "DRAFT" -> WorkshopStateContract.DRAFT;
            case "PLANNED" -> WorkshopStateContract.PLANNED;
            case "PUBLISHED" -> WorkshopStateContract.PUBLISHED;
            case "IN_PROGRESS" -> WorkshopStateContract.IN_PROGRESS;
            case "COMPLETED" -> WorkshopStateContract.COMPLETED;
            case "CANCELLED" -> WorkshopStateContract.CANCELLED;
            default -> throw new IllegalStateException("Unknown workshop state: " + state);
        };
    }

    private static WorkshopStateContract mapState(WorkshopState state) {
        return switch (state) {
            case DRAFT -> WorkshopStateContract.DRAFT;
            case PLANNED -> WorkshopStateContract.PLANNED;
            case PUBLISHED -> WorkshopStateContract.PUBLISHED;
            case IN_PROGRESS -> WorkshopStateContract.IN_PROGRESS;
            case COMPLETED -> WorkshopStateContract.COMPLETED;
            case CANCELLED -> WorkshopStateContract.CANCELLED;
        };
    }
}
