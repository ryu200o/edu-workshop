package io.github.ryu200o.eduworkshop.room.internal.application.port.out;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.RoomResponse;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.value.RoomName;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only outbound port (SPI) for the Room read side. Consumer-Driven: it declares only the lookups
 * the query use cases actually need. Returns {@link RoomResponse} projections directly (CQRS bypass —
 * no domain aggregate reconstruction). Implementations must be side-effect free.
 */
public interface RoomQueryPort {

    Optional<RoomResponse> findById(UUID id);

    Optional<RoomResponse> findByName(RoomName name);
}
