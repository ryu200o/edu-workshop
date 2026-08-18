package io.github.ryu200o.eduworkshop.iam.internal.adapter.outbound.persistence.jooq;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.UserReader;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;
import io.github.ryu200o.eduworkshop.iam.jooq.tables.IamUserRoles;
import io.github.ryu200o.eduworkshop.iam.jooq.tables.IamUsers;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * JOOQ-backed outbound adapter implementing the IAM read port ({@link UserReader}). Queries the
 * {@code iam_users} table (plus its {@code iam_user_roles} child rows) directly via the generated
 * {@link IamUsers} model and maps flat columns into the read-side {@code *View} projections — no JPA
 * entity, no domain aggregate reconstruction (CQRS bypass). Shares the module's single datasource
 * with the write adapter. Package-private; hidden inside the module's {@code internal} boundary.
 */
@Component
class JooqUserReadAdapter implements UserReader {

    private static final IamUsers USERS = IamUsers.IAM_USERS;
    private static final IamUserRoles USER_ROLES = IamUserRoles.IAM_USER_ROLES;

    private final DSLContext dsl;

    JooqUserReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<UserSummaryView> getById(UserId id) {
        return dsl.select(
                        USERS.ID,
                        USERS.EMAIL,
                        USERS.FULL_NAME,
                        USERS.AVATAR_URL,
                        USERS.STATUS)
                .from(USERS)
                .where(USERS.ID.eq(id.value()))
                .fetchOptional()
                .map(record -> toSummaryView(record, loadRoles(id.value())));
    }

    @Override
    public Optional<MeView> getMe(UserId id) {
        return dsl.select(
                        USERS.ID,
                        USERS.EMAIL,
                        USERS.FULL_NAME,
                        USERS.PHONE_NUMBER,
                        USERS.STUDENT_CODE,
                        USERS.AVATAR_URL,
                        USERS.STATUS,
                        USERS.MUST_CHANGE_PASSWORD,
                        USERS.CREATED_AT)
                .from(USERS)
                .where(USERS.ID.eq(id.value()))
                .fetchOptional()
                .map(record -> new MeView(
                        record.get(USERS.ID),
                        record.get(USERS.EMAIL),
                        record.get(USERS.FULL_NAME),
                        record.get(USERS.PHONE_NUMBER),
                        record.get(USERS.STUDENT_CODE),
                        record.get(USERS.AVATAR_URL),
                        record.get(USERS.STATUS),
                        loadRoles(id.value()),
                        record.get(USERS.MUST_CHANGE_PASSWORD),
                        record.get(USERS.CREATED_AT).toInstant()));
    }

    @Override
    public Optional<UserDetailView> getDetail(UserId id) {
        return dsl.select(
                        USERS.ID,
                        USERS.EMAIL,
                        USERS.FULL_NAME,
                        USERS.PHONE_NUMBER,
                        USERS.STUDENT_CODE,
                        USERS.AVATAR_URL,
                        USERS.STATUS,
                        USERS.MUST_CHANGE_PASSWORD,
                        USERS.CREATED_AT,
                        USERS.UPDATED_AT,
                        USERS.FAILED_LOGIN_ATTEMPTS,
                        USERS.LOCKOUT_COUNT,
                        USERS.LOCKED_UNTIL,
                        USERS.LAST_LOCKED_AT)
                .from(USERS)
                .where(USERS.ID.eq(id.value()))
                .fetchOptional()
                .map(record -> new UserDetailView(
                        record.get(USERS.ID),
                        record.get(USERS.EMAIL),
                        record.get(USERS.FULL_NAME),
                        record.get(USERS.PHONE_NUMBER),
                        record.get(USERS.STUDENT_CODE),
                        record.get(USERS.AVATAR_URL),
                        record.get(USERS.STATUS),
                        loadRoles(id.value()),
                        record.get(USERS.MUST_CHANGE_PASSWORD),
                        record.get(USERS.CREATED_AT).toInstant(),
                        record.get(USERS.UPDATED_AT).toInstant(),
                        record.get(USERS.FAILED_LOGIN_ATTEMPTS),
                        record.get(USERS.LOCKOUT_COUNT),
                        toInstant(record.get(USERS.LOCKED_UNTIL)),
                        toInstant(record.get(USERS.LAST_LOCKED_AT))));
    }

    @Override
    public List<UserSummaryView> list() {
        return dsl.select(
                        USERS.ID,
                        USERS.EMAIL,
                        USERS.FULL_NAME,
                        USERS.AVATAR_URL,
                        USERS.STATUS)
                .from(USERS)
                .orderBy(USERS.CREATED_AT.desc())
                .fetch()
                .map(record -> toSummaryView(record, loadRoles(record.get(USERS.ID))));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private Set<String> loadRoles(UUID userId) {
        return new LinkedHashSet<>(dsl.select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .orderBy(USER_ROLES.ROLE)
                .fetch(USER_ROLES.ROLE));
    }

    private static UserSummaryView toSummaryView(Record record, Set<String> roles) {
        return new UserSummaryView(
                record.get(USERS.ID),
                record.get(USERS.EMAIL),
                record.get(USERS.FULL_NAME),
                record.get(USERS.AVATAR_URL),
                record.get(USERS.STATUS),
                roles
        );
    }
}