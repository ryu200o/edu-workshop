package io.github.ryu200o.eduworkshop.facilityops.internal.application.handler;

import io.github.ryu200o.eduworkshop.facilityops.internal.application.exception.FacilityRoomNotFoundException;
import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.PreviewRoomMaintenanceImpactQuery;
import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.view.RoomMaintenanceImpactView;
import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopImpactDto;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Query handler for {@link PreviewRoomMaintenanceImpactQuery}. Hosted by the FacilityOps module
 * (top of the dependency DAG) and aggregates data exclusively through the public {@code *ExposeAPI}
 * interfaces — no access to any other module's {@code internal} packages (ADR 0010, zero cyclic
 * dependency). The Room module stays a pure facility owner; impact analysis belongs to FacilityOps.
 */
@Component
class PreviewRoomMaintenanceImpactQueryHandler
        implements QueryHandler<PreviewRoomMaintenanceImpactQuery, RoomMaintenanceImpactView> {

    private final RoomExposeAPI roomExposeAPI;
    private final WorkshopExposeAPI workshopExposeAPI;
    private final RegistrationExposeAPI registrationExposeAPI;

    PreviewRoomMaintenanceImpactQueryHandler(RoomExposeAPI roomExposeAPI,
                                             WorkshopExposeAPI workshopExposeAPI,
                                             RegistrationExposeAPI registrationExposeAPI) {
        this.roomExposeAPI = roomExposeAPI;
        this.workshopExposeAPI = workshopExposeAPI;
        this.registrationExposeAPI = registrationExposeAPI;
    }

    @Override
    public RoomMaintenanceImpactView handle(PreviewRoomMaintenanceImpactQuery query) {
        if (!roomExposeAPI.existsById(query.roomId())) {
            throw new FacilityRoomNotFoundException(query.roomId());
        }

        List<WorkshopImpactDto> workshops = workshopExposeAPI.findByRoomAndTimeOverlap(
                query.roomId(), query.startTime(), query.endTime());

        int publishedCount = 0;
        int plannedCount = 0;
        List<UUID> publishedIds = new ArrayList<>();

        for (WorkshopImpactDto ws : workshops) {
            switch (ws.state()) {
                case PUBLISHED -> {
                    publishedCount++;
                    publishedIds.add(ws.id());
                }
                case PLANNED -> plannedCount++;
                default -> {
                }
            }
        }

        int totalStudents = publishedIds.isEmpty()
                ? 0
                : registrationExposeAPI.countActiveByWorkshopIds(publishedIds);

        return new RoomMaintenanceImpactView(query.roomId(), publishedCount, plannedCount, totalStudents);
    }
}
