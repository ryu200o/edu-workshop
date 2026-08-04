package io.github.ryu200o.eduworkshop.room;

import io.github.ryu200o.eduworkshop.room.contract.RoomPlanningPermission;

import java.util.Optional;
import java.util.UUID;

public interface RoomExposeAPI {

    Optional<RoomPlanningPermission> findPlanningPermission(UUID roomId);

    /**
     * Whether a room with the given id exists. Used by upper-layer modules (e.g. FacilityOps) for
     * fast existence checks before composing cross-module reads.
     */
    boolean existsById(UUID roomId);
}
