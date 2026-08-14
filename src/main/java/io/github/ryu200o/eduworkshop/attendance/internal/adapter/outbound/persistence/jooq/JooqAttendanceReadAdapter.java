package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceLedgerEntryView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRecordLedgerView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterEntryView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.inbound.query.view.AttendanceRosterView;
import io.github.ryu200o.eduworkshop.attendance.internal.application.port.outbound.AttendanceRecordReader;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.ActorRole;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceAction;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceState;
import io.github.ryu200o.eduworkshop.attendance.jooq.tables.AttendanceEntries;
import io.github.ryu200o.eduworkshop.attendance.jooq.tables.AttendanceRecords;
import io.github.ryu200o.eduworkshop.attendance.jooq.tables.records.AttendanceEntriesRecord;
import io.github.ryu200o.eduworkshop.attendance.jooq.tables.records.AttendanceRecordsRecord;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JOOQ-backed outbound adapter implementing the Attendance read port ({@link AttendanceRecordReader}).
 * Queries {@code attendance_records} / {@code attendance_entries} directly via the generated model
 * and returns task-tailored {@code *View} projections — no JPA entity, no domain aggregate
 * reconstruction (CQRS bypass, ADR 0017). Read-only by design.
 */
@Component
class JooqAttendanceReadAdapter implements AttendanceRecordReader {

    private static final AttendanceRecords AR = AttendanceRecords.ATTENDANCE_RECORDS;
    private static final AttendanceEntries AE = AttendanceEntries.ATTENDANCE_ENTRIES;

    private static final RecordMapper<AttendanceRecordsRecord, AttendanceRosterEntryView> TO_ROSTER_ENTRY =
            record -> new AttendanceRosterEntryView(
                    record.getId(),
                    record.getStudentId(),
                    AttendanceResult.valueOf(record.getCurrentResult()),
                    AttendanceState.valueOf(record.getState()),
                    toInstant(record.getUpdatedAt()));

    private static final RecordMapper<AttendanceEntriesRecord, AttendanceLedgerEntryView> TO_LEDGER_ENTRY =
            record -> new AttendanceLedgerEntryView(
                    record.getEntryNumber(),
                    toInstant(record.getTimestamp()),
                    record.getActorId(),
                    ActorRole.valueOf(record.getActorRole()),
                    AttendanceAction.valueOf(record.getAction()),
                    AttendanceResult.valueOf(record.getResult()),
                    record.getReason(),
                    record.getEvidenceReference());

    private final DSLContext dsl;

    JooqAttendanceReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<AttendanceRecordLedgerView> getById(UUID recordId) {
        var master = dsl.selectFrom(AR)
                .where(AR.ID.eq(recordId))
                .fetchOptional();
        if (master.isEmpty()) {
            return Optional.empty();
        }
        AttendanceRecordsRecord record = master.get();
        List<AttendanceLedgerEntryView> entries = dsl.selectFrom(AE)
                .where(AE.RECORD_ID.eq(recordId))
                .orderBy(AE.ENTRY_NUMBER.asc())
                .fetch(TO_LEDGER_ENTRY);
        return Optional.of(new AttendanceRecordLedgerView(
                record.getId(),
                record.getStudentId(),
                record.getWorkshopId(),
                AttendanceResult.valueOf(record.getCurrentResult()),
                AttendanceState.valueOf(record.getState()),
                toInstant(record.getReconciliationStartedAt()),
                entries,
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt())));
    }

    @Override
    public AttendanceRosterView getByWorkshopId(UUID workshopId,
                                                AttendanceResult resultFilter,
                                                AttendanceState stateFilter) {
        Condition condition = AR.WORKSHOP_ID.eq(workshopId);
        if (resultFilter != null) {
            condition = condition.and(AR.CURRENT_RESULT.eq(resultFilter.name()));
        }
        if (stateFilter != null) {
            condition = condition.and(AR.STATE.eq(stateFilter.name()));
        }

        List<AttendanceRosterEntryView> records = dsl.selectFrom(AR)
                .where(condition)
                .orderBy(AR.UPDATED_AT.asc())
                .fetch(TO_ROSTER_ENTRY);

        Map<String, Integer> counts = dsl.select(AR.CURRENT_RESULT, DSL.count())
                .from(AR)
                .where(condition)
                .groupBy(AR.CURRENT_RESULT)
                .fetchMap(AR.CURRENT_RESULT, DSL.count());

        return new AttendanceRosterView(
                workshopId,
                records.size(),
                counts.getOrDefault(AttendanceResult.PRESENT.name(), 0),
                counts.getOrDefault(AttendanceResult.LATE.name(), 0),
                counts.getOrDefault(AttendanceResult.ABSENT.name(), 0),
                counts.getOrDefault(AttendanceResult.EXCUSED.name(), 0),
                records);
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}