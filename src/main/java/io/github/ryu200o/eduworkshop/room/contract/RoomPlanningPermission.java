package io.github.ryu200o.eduworkshop.room.contract;

import java.util.Objects;
import java.util.UUID;

public record RoomPlanningPermission(
        PlanningStatus status,
        String reason,
        RoomPlanningData planning
) {

    public RoomPlanningPermission {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(planning, "planning must not be null");
    }

    public enum PlanningStatus {
        ALLOWED,
        WARNING,
        BLOCKED
    }

    public record RoomPlanningData(
            UUID roomId,
            String roomName,
            Location location,
            int capacity
    ) {
        public record Location(String building, int floor) {

            public Location {
                if (building == null || building.isBlank()) {
                    throw new IllegalArgumentException("building must not be blank");
                }
                if (floor < 0) {
                    throw new IllegalArgumentException("floor must be non-negative");
                }
            }
        }
    }
}
