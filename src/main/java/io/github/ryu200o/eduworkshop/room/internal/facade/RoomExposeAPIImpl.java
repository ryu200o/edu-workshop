package io.github.ryu200o.eduworkshop.room.internal.facade;

import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.PlanningStatus;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData;
import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission.RoomPlanningData.Location;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomReader;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class RoomExposeAPIImpl implements RoomExposeAPI {

    private final RoomReader roomReader;

    RoomExposeAPIImpl(RoomReader roomReader) {
        this.roomReader = roomReader;
    }

    @Override
    public Optional<RoomPlanningPermission> checkPlanningPermission(UUID roomId) {
        return roomReader.findById(RoomId.of(roomId))
                .map(view -> {
                    PlanningStatus status = switch (view.state()) {
                        case "ACTIVE" -> PlanningStatus.ALLOWED;
                        case "MAINTENANCE" -> PlanningStatus.WARNING;
                        case "DEACTIVATED" -> PlanningStatus.BLOCKED;
                        default -> throw new IllegalStateException("Unknown room state: " + view.state());
                    };
                    String reason = switch (status) {
                        case ALLOWED -> null;
                        case WARNING -> "Room is under maintenance";
                        case BLOCKED -> "Room is deactivated permanently";
                    };
                    RoomPlanningData data = new RoomPlanningData(
                            view.id(),
                            view.name(),
                            new Location(view.building(), view.floor()),
                            view.capacity()
                    );
                    return new RoomPlanningPermission(status, reason, data);
                });
    }
}
