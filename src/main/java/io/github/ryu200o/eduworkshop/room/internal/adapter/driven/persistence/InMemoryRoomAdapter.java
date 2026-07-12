package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence;

import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomExistencePort;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomStateGateway;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.entity.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory driven adapter backing both Room outbound ports with a {@link ConcurrentHashMap}.
 * Keeps the Spring context wired and {@code contextLoads} green until a real JPA adapter arrives.
 * Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class InMemoryRoomAdapter implements RoomExistencePort, RoomStateGateway {

    private final Map<UUID, Room> store = new ConcurrentHashMap<>();

    @Override
    public boolean existsByNameAndLocation(RoomName name, RoomLocation location) {
        return store.values().stream()
                .anyMatch(room -> room.name().equals(name) && room.location().equals(location));
    }

    @Override
    public Room save(Room room) {
        store.put(room.id(), room);
        return room;
    }
}
