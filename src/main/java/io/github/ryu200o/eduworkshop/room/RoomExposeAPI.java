package io.github.ryu200o.eduworkshop.room;

import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;

import java.util.Optional;
import java.util.UUID;

public interface RoomExposeAPI {

    Optional<RoomPlanningPermission> checkPlanningPermission(UUID roomId);
}
