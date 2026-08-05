package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side outbound port (SPI) for the Workshop read side. Consumer-Driven: it declares only the
 * lookups the query use cases actually need. Returns read-side {@code *View} projections directly
 * (CQRS bypass — no domain aggregate reconstruction). Implementations must be side-effect free.
 */
public interface WorkshopReader {

    /**
     * Looks up a workshop's full detail by id. Returns {@link WorkshopDetailView} (full projection).
     */
    Optional<WorkshopDetailView> findById(UUID id);

    /**
     * Lists all workshops as lightweight summaries. Returns an empty list when no workshops exist.
     */
    List<WorkshopSummaryView> findAll();

    /**
     * Finds workshops assigned to a given room whose time window overlaps the specified range.
     * Only planning-relevant states ({@code PUBLISHED}, {@code PLANNED}) are returned — the state
     * predicate is pushed down to SQL (DB Query Pushdown), never filtered in memory. Overlap
     * condition: {@code wsStart < maintEnd && wsEnd > maintStart}. If {@code maintEnd} is null
     * (indefinite maintenance), all workshops starting after {@code maintStart} match.
     *
     * @param roomId    the room to filter by
     * @param startTime the maintenance window start (inclusive lower bound)
     * @param endTime   the maintenance window end (null = indefinite)
     * @return list of matching workshops as lightweight summaries
     */
    List<WorkshopSummaryView> findByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime);
}
