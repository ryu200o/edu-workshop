package io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query;

import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.view.RoomMaintenanceImpactView;
import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side query to preview the impact of a maintenance window before scheduling.
 * Returns counts of affected PUBLISHED/PLANNED workshops and their active students.
 * Hosted by the FacilityOps module (top of the dependency DAG) — it aggregates data through the
 * public {@code *ExposeAPI} interfaces of the Room, Workshop and Registration modules (ADR 0010).
 *
 * @param roomId    the room to preview
 * @param startTime the maintenance window start
 * @param endTime   the maintenance window end (null = indefinite)
 */
public record PreviewRoomMaintenanceImpactQuery(
        UUID roomId,
        Instant startTime,
        Instant endTime
) implements Query<RoomMaintenanceImpactView> {
}
