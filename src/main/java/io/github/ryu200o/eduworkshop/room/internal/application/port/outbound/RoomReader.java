package io.github.ryu200o.eduworkshop.room.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.inbound.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

import java.util.Optional;

/**
 * Read-side outbound port (SPI) for the Room read side. Consumer-Driven: it declares only the lookups
 * the query use cases actually need. Returns read-side {@code *View} projections directly (CQRS bypass
 * — no domain aggregate reconstruction). Implementations must be side-effect free.
 */
public interface RoomReader {

    /**
     * Returns whether a room with the given id exists. Pure existence predicate — pushed down to
     * SQL ({@code SELECT EXISTS}) so no projection is materialized on the read side.
     */
    boolean existsById(RoomId id);

    /**
     * Looks up a room's full detail by id. Returns {@link RoomDetailView} (full projection).
     */
    Optional<RoomDetailView> getById(RoomId id);

    /**
     * Looks up a room by its canonical display name. The {@code RoomName} is an opaque, type-safe token
     * (matched exactly against the stored name string — it is never reverse-parsed into coordinates),
     * so this port takes the value object directly for RAM-side type safety. Returns {@link
     * RoomSummaryView} (flattened summary).
     */
    Optional<RoomSummaryView> getByName(RoomName name);
}
