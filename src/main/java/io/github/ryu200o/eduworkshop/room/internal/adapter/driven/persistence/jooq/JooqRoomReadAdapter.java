package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jooq;

import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomDetailView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.in.query.view.RoomSummaryView;
import io.github.ryu200o.eduworkshop.room.internal.application.port.out.RoomQueryPort;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;
import io.github.ryu200o.eduworkshop.room.jooq.tables.Rooms;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JOOQ-backed driven adapter implementing the Room read port ({@link RoomQueryPort}). Queries the
 * {@code rooms} table directly via the generated {@link Rooms} model and maps flat columns into the
 * read-side {@code *View} projections — no JPA entity, no domain aggregate reconstruction (CQRS bypass).
 * Shares the module's single datasource with the write adapter. Package-private; hidden inside the
 * module's {@code internal} boundary.
 */
@Component
class JooqRoomReadAdapter implements RoomQueryPort {

    private static final Rooms ROOMS = Rooms.ROOMS;

    private final DSLContext dsl;

    JooqRoomReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<RoomDetailView> findById(RoomId id) {
        return dsl.select(
                        ROOMS.ID,
                        ROOMS.NAME,
                        ROOMS.BUILDING,
                        ROOMS.FLOOR,
                        ROOMS.CAPACITY,
                        ROOMS.STATE)
                .from(ROOMS)
                .where(ROOMS.ID.eq(id.value()))
                .fetchOptional()
                .map(JooqRoomReadAdapter::toDetailView);
    }

    @Override
    public Optional<RoomSummaryView> findByName(RoomName name) {
        return dsl.select(
                        ROOMS.ID,
                        ROOMS.NAME,
                        ROOMS.BUILDING,
                        ROOMS.FLOOR)
                .from(ROOMS)
                .where(ROOMS.NAME.eq(name.asString()))
                .fetchOptional()
                .map(JooqRoomReadAdapter::toSummaryView);
    }

    private static RoomDetailView toDetailView(Record record) {
        return new RoomDetailView(
                record.get(ROOMS.ID),
                record.get(ROOMS.NAME),
                record.get(ROOMS.BUILDING),
                record.get(ROOMS.FLOOR),
                record.get(ROOMS.CAPACITY),
                record.get(ROOMS.STATE)
        );
    }

    private static RoomSummaryView toSummaryView(Record record) {
        return new RoomSummaryView(
                record.get(ROOMS.ID),
                record.get(ROOMS.NAME),
                record.get(ROOMS.BUILDING),
                record.get(ROOMS.FLOOR)
        );
    }
}
