package io.github.ryu200o.eduworkshop.workshop.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopOverlapView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import io.github.ryu200o.eduworkshop.workshop.jooq.tables.Workshops;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JooqWorkshopReadAdapter implements WorkshopReader {

    private static final Workshops WORKSHOPS = Workshops.WORKSHOPS;

    private final DSLContext dsl;

    JooqWorkshopReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkshopDetailView> getById(UUID id) {
        return dsl.select(
                        WORKSHOPS.ID,
                        WORKSHOPS.TITLE,
                        WORKSHOPS.DESCRIPTION,
                        WORKSHOPS.ROOM_ID,
                        WORKSHOPS.ROOM_NAME_SNAPSHOT,
                        WORKSHOPS.ROOM_LOCATION_SNAPSHOT,
                        WORKSHOPS.ROOM_CAPACITY_SNAPSHOT,
                        WORKSHOPS.HAS_ROOM_WARNING,
                        WORKSHOPS.IS_ROOM_EVICTED,
                        WORKSHOPS.ROOM_EVICTED_AT,
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
    public List<WorkshopSummaryView> getAll() {
        return dsl.select(
                        WORKSHOPS.ID,
                        WORKSHOPS.TITLE,
                        WORKSHOPS.START_TIME,
                        WORKSHOPS.END_TIME,
                        WORKSHOPS.IS_ROOM_EVICTED,
                        WORKSHOPS.ROOM_EVICTED_AT,
                        WORKSHOPS.STATE)
                .from(WORKSHOPS)
                .fetch()
                .map(JooqWorkshopReadAdapter::toSummaryView);
    }

    @Override
    public List<WorkshopOverlapView> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime) {
        // Occupancy Window (ADR 0018): a workshop occupies the room from occupancy_start to end_time,
        // where occupancy_start = start_time - currentConfigBuffer (denormalized). Overlap with the
        // maintenance window is decided on that window via the native SQL predicate (approved by the
        // composite index idx_workshops_room_occupancy (room_id, occupancy_start, end_time));
        // no in-memory filter / widened superset.
        var condition = WORKSHOPS.ROOM_ID.eq(roomId)
                .and(WORKSHOPS.STATE.in("PUBLISHED", "PLANNED"))
                .and(WORKSHOPS.END_TIME.greaterThan(OffsetDateTime.ofInstant(startTime, java.time.ZoneOffset.UTC)));
        if (endTime != null) {
            condition = condition.and(WORKSHOPS.OCCUPANCY_START.lessThan(OffsetDateTime.ofInstant(endTime, java.time.ZoneOffset.UTC)));
        }
        return dsl.select(
                        WORKSHOPS.ID,
                        WORKSHOPS.STATE)
                .from(WORKSHOPS)
                .where(condition)
                .fetch()
                .map(JooqWorkshopReadAdapter::toOverlapView);
    }

    @Override
    public List<WorkshopIdView> getPublishedDueToStart(Instant now) {
        return dsl.select(WORKSHOPS.ID)
                .from(WORKSHOPS)
                .where(WORKSHOPS.STATE.eq("PUBLISHED"))
                .and(WORKSHOPS.START_TIME.lessOrEqual(OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC)))
                .fetch()
                .map(JooqWorkshopReadAdapter::toIdView);
    }

    @Override
    public List<WorkshopIdView> getInProgressDueToComplete(Instant now) {
        return dsl.select(WORKSHOPS.ID)
                .from(WORKSHOPS)
                .where(WORKSHOPS.STATE.eq("IN_PROGRESS"))
                .and(WORKSHOPS.END_TIME.lessOrEqual(OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC)))
                .fetch()
                .map(JooqWorkshopReadAdapter::toIdView);
    }

    @Override
    public List<WorkshopIdView> getPublishedOverdueByEndTime(Instant now) {
        return dsl.select(WORKSHOPS.ID)
                .from(WORKSHOPS)
                .where(WORKSHOPS.STATE.eq("PUBLISHED"))
                .and(WORKSHOPS.END_TIME.lessThan(OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC)))
                .fetch()
                .map(JooqWorkshopReadAdapter::toIdView);
    }

    private static WorkshopIdView toIdView(Record record) {
        return new WorkshopIdView(record.get(WORKSHOPS.ID));
    }

    private static WorkshopOverlapView toOverlapView(Record record) {
        return new WorkshopOverlapView(
                record.get(WORKSHOPS.ID),
                record.get(WORKSHOPS.STATE)
        );
    }

    private static WorkshopDetailView toDetailView(Record record) {
        return new WorkshopDetailView(
                record.get(WORKSHOPS.ID),
                record.get(WORKSHOPS.TITLE),
                record.get(WORKSHOPS.DESCRIPTION),
                record.get(WORKSHOPS.ROOM_ID),
                record.get(WORKSHOPS.ROOM_NAME_SNAPSHOT),
                record.get(WORKSHOPS.ROOM_LOCATION_SNAPSHOT),
                record.get(WORKSHOPS.ROOM_CAPACITY_SNAPSHOT),
                record.get(WORKSHOPS.HAS_ROOM_WARNING) != null && record.get(WORKSHOPS.HAS_ROOM_WARNING),
                record.get(WORKSHOPS.IS_ROOM_EVICTED) != null && record.get(WORKSHOPS.IS_ROOM_EVICTED),
                toInstant(record.get(WORKSHOPS.ROOM_EVICTED_AT)),
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
                record.get(WORKSHOPS.IS_ROOM_EVICTED) != null && record.get(WORKSHOPS.IS_ROOM_EVICTED),
                toInstant(record.get(WORKSHOPS.ROOM_EVICTED_AT)),
                record.get(WORKSHOPS.STATE)
        );
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
