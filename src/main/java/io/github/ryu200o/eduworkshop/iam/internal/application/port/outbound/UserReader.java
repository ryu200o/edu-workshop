package io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.query.view.UserSummaryView;
import io.github.ryu200o.eduworkshop.iam.internal.domain.model.UserId;

import java.util.Optional;

/**
 * Read-side outbound port (SPI) for the IAM read side. Consumer-Driven: it declares only the lookups
 * the query use cases actually need. Returns read-side {@code *View} projections directly (CQRS
 * bypass — no domain aggregate reconstruction). Implementations must be side-effect free. Read ports
 * use {@code get*} naming (ADR 0016).
 */
public interface UserReader {

    /**
     * Looks up a user's summary projection by id (used by the Module Facade and self/admin queries).
     */
    Optional<UserSummaryView> getById(UserId id);
}
