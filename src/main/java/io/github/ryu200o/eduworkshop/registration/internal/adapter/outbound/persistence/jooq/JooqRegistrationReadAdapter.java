package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.MyRegistrationStatus;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.inbound.query.view.MyRegistrationView;
import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
import io.github.ryu200o.eduworkshop.registration.jooq.tables.Registrations;
import io.github.ryu200o.eduworkshop.registration.jooq.tables.records.RegistrationsRecord;

import org.jooq.DSLContext;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JOOQ-backed outbound adapter implementing the Registration read port ({@link RegistrationReader}).
 * Queries the {@code registrations} table directly via the generated {@link Registrations} model and
 * returns primitives / projections — no JPA entity, no domain aggregate reconstruction (CQRS bypass).
 * Shares the module's single datasource with the write adapter. Package-private; hidden inside the
 * module's {@code internal} boundary.
 */
@Component
class JooqRegistrationReadAdapter implements RegistrationReader {

    private static final Registrations REGISTRATIONS = Registrations.REGISTRATIONS;

    private static final RecordMapper<RegistrationsRecord, MyRegistrationView> TO_MY_REGISTRATION_VIEW =
            record -> new MyRegistrationView(
                    record.getId(),
                    record.getWorkshopId(),
                    record.getWorkshopTitleSnapshot(),
                    toInstant(record.getWorkshopStartTime()),
                    toInstant(record.getWorkshopEndTimeSnapshot()),
                    record.getWorkshopRoomNameSnapshot(),
                    record.getUserId(),
                    MyRegistrationStatus.valueOf(record.getStatus()),
                    toInstant(record.getRegisteredAt()),
                    toInstant(record.getCancelledAt()));

    private final DSLContext dsl;

    JooqRegistrationReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public int countActiveByWorkshop(UUID workshopId) {
        return dsl.fetchCount(
                dsl.selectFrom(REGISTRATIONS)
                        .where(REGISTRATIONS.WORKSHOP_ID.eq(workshopId))
                        .and(REGISTRATIONS.STATUS.eq("REGISTERED")));
    }

    @Override
    public int countActiveByWorkshopIds(List<UUID> workshopIds) {
        if (workshopIds == null || workshopIds.isEmpty()) {
            return 0;
        }
        return dsl.fetchCount(
                dsl.selectFrom(REGISTRATIONS)
                        .where(REGISTRATIONS.WORKSHOP_ID.in(workshopIds))
                        .and(REGISTRATIONS.STATUS.eq("REGISTERED")));
    }

    @Override
    public List<MyRegistrationView> getByUserId(UUID userId, MyRegistrationStatus status) {
        var select = dsl.selectFrom(REGISTRATIONS)
                .where(REGISTRATIONS.USER_ID.eq(userId));
        if (status != null) {
            select.and(REGISTRATIONS.STATUS.eq(status.name()));
        }
        return select.orderBy(REGISTRATIONS.REGISTERED_AT.desc())
                .fetch(TO_MY_REGISTRATION_VIEW);
    }

    @Override
    public Optional<MyRegistrationStatus> getStatusByWorkshopAndUser(UUID workshopId, UUID userId) {
        return dsl.select(REGISTRATIONS.STATUS)
                .from(REGISTRATIONS)
                .where(REGISTRATIONS.WORKSHOP_ID.eq(workshopId))
                .and(REGISTRATIONS.USER_ID.eq(userId))
                .fetchOptional(REGISTRATIONS.STATUS)
                .map(MyRegistrationStatus::valueOf);
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
