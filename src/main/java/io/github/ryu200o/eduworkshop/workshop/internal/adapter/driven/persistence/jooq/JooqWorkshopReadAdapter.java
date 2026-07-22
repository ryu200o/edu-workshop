package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jooq;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.jooq.tables.Workshops;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JOOQ-backed driven adapter implementing the Workshop read port ({@link WorkshopReader}). Queries the
 * {@code workshops} table directly via the generated {@link Workshops} model and maps flat columns into
 * the read-side {@code *View} projections — no JPA entity, no domain aggregate reconstruction (CQRS bypass).
 * Shares the module's single datasource with the write adapter. Package-private; hidden inside the
 * module's {@code internal} boundary.
 */
@Component
class JooqWorkshopReadAdapter implements WorkshopReader {

    private static final Workshops WORKSHOPS = Workshops.WORKSHOPS;

    private final DSLContext dsl;

    JooqWorkshopReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkshopDetailView> findById(UUID id) {
        return dsl.select(
                        WORKSHOPS.ID,
                        WORKSHOPS.TITLE,
                        WORKSHOPS.DESCRIPTION,
                        WORKSHOPS.ROOM_ID,
                        WORKSHOPS.ROOM_NAME_SNAPSHOT,
                        WORKSHOPS.ROOM_LOCATION_SNAPSHOT,
                        WORKSHOPS.START_TIME,
                        WORKSHOPS.END_TIME,
                        WORKSHOPS.CAPACITY,
                        WORKSHOPS.STATE,
                        WORKSHOPS.CREATED_AT,
                        WORKSHOPS.UPDATED_AT)
                .from(WORKSHOPS)
                .where(WORKSHOPS.ID.eq(id))
                .fetchOptional()
                .map(JooqWorkshopReadAdapter::toDetailView);
    }

    @Override
    public List<WorkshopSummaryView> findAll() {
        return dsl.select(
                        WORKSHOPS.ID,
                        WORKSHOPS.TITLE,
                        WORKSHOPS.START_TIME,
                        WORKSHOPS.END_TIME,
                        WORKSHOPS.STATE)
                .from(WORKSHOPS)
                .fetch()
                .map(JooqWorkshopReadAdapter::toSummaryView);
    }

    private static WorkshopDetailView toDetailView(Record record) {
        return new WorkshopDetailView(
                record.get(WORKSHOPS.ID),
                record.get(WORKSHOPS.TITLE),
                record.get(WORKSHOPS.DESCRIPTION),
                record.get(WORKSHOPS.ROOM_ID),
                record.get(WORKSHOPS.ROOM_NAME_SNAPSHOT),
                record.get(WORKSHOPS.ROOM_LOCATION_SNAPSHOT),
                toInstant(record.get(WORKSHOPS.START_TIME)),
                toInstant(record.get(WORKSHOPS.END_TIME)),
                record.get(WORKSHOPS.CAPACITY),
                record.get(WORKSHOPS.STATE),
                toInstant(record.get(WORKSHOPS.CREATED_AT)),
                toInstant(record.get(WORKSHOPS.UPDATED_AT))
        );
    }

    private static WorkshopSummaryView toSummaryView(Record record) {
        return new WorkshopSummaryView(
                record.get(WORKSHOPS.ID),
                record.get(WORKSHOPS.TITLE),
                toInstant(record.get(WORKSHOPS.START_TIME)),
                toInstant(record.get(WORKSHOPS.END_TIME)),
                record.get(WORKSHOPS.STATE)
        );
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
