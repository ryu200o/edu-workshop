package io.github.ryu200o.eduworkshop.facilityops.internal.adapter.inbound.http;

import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.PreviewRoomMaintenanceImpactQuery;
import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.view.RoomMaintenanceImpactView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.shared.security.api.policy.CanManageRooms;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Driving HTTP adapter for the FacilityOps READ side (Query). Accepts only data-reading HTTP methods
 * (GET) and talks exclusively to the shared {@link QueryBus}. Package-private and confined to the
 * module's internal boundary. Error handling is centralized in {@link FacilityOpsExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/facility-ops")
@CanManageRooms
class FacilityOpsQueryController {

    private final QueryBus queryBus;

    FacilityOpsQueryController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/rooms/{id}/maintenance-impact-preview")
    RoomMaintenanceImpactView previewImpact(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
        var query = new PreviewRoomMaintenanceImpactQuery(id, startTime, endTime);
        return queryBus.execute(query);
    }
}
