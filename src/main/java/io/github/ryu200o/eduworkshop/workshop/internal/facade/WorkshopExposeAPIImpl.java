package io.github.ryu200o.eduworkshop.workshop.internal.facade;

import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopRegistrationContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;

import org.springframework.stereotype.Component;

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

    WorkshopExposeAPIImpl(WorkshopReader workshopReader) {
        this.workshopReader = workshopReader;
    }

    @Override
    public Optional<WorkshopRegistrationContract> findForRegistration(UUID workshopId) {
        return workshopReader.findById(workshopId)
                .map(view -> new WorkshopRegistrationContract(view.id(), mapState(view.state()), view.startTime()));
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
}
