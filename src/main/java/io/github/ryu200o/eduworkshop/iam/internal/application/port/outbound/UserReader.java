package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.MeView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserDetailView;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Read-side outbound port (SPI) for the IAM read side. Consumer-Driven: it declares only the lookups
 * the query use cases actually need. Returns read-side {@code *View} projections directly (CQRS
 * bypass — no domain aggregate reconstruction). Implementations must be side-effect free. Read ports
 * use {@code get*} naming (ADR 0016).
 */
public interface UserReader {

    /**
     * Looks up a user's summary projection by id (used by the Module Facade and directory listing).
     */
    Optional<UserSummaryView> getById(UserId id);

    /**
     * Looks up the authenticated caller's own profile projection by id (used by {@code GetMeQuery}).
     */
    Optional<MeView> getMe(UserId id);

    /**
     * Looks up the admin detail projection by id — self-profile fields plus the account-security
     * counters (used by {@code GetUserDetailQuery}).
     */
    Optional<UserDetailView> getDetail(UserId id);

    /**
     * Lists every account as a summary projection, newest first (used by {@code ListUsersQuery}).
     */
    List<UserSummaryView> list();
}
