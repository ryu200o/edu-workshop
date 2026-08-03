package io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.view;

import java.util.UUID;

/**
 * Read projection (View) for the maintenance impact preview query. Shows how many workshops
 * and students would be affected by a maintenance window.
 *
 * @param roomId                     the room being previewed
 * @param publishedWorkshopsCount    number of PUBLISHED workshops overlapping the window
 * @param plannedWorkshopsCount      number of PLANNED workshops overlapping the window
 * @param totalAffectedStudentsCount total active registrations across affected PUBLISHED workshops
 */
public record RoomMaintenanceImpactView(
        UUID roomId,
        int publishedWorkshopsCount,
        int plannedWorkshopsCount,
        int totalAffectedStudentsCount
) {
}
