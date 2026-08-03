package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.registration.internal.application.port.outbound.RegistrationReader;
import io.github.ryu200o.eduworkshop.registration.jooq.tables.Registrations;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;
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
}
