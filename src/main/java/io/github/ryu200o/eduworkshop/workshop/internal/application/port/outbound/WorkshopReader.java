package io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopOverlapView;
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
    Optional<WorkshopDetailView> getById(UUID id);

    /**
     * Lists all workshops as lightweight summaries. Returns an empty list when no workshops exist.
     */
    List<WorkshopSummaryView> getAll();

    /**
     * Gets workshops assigned to a given room whose time window overlaps the specified range.
     * Only planning-relevant states ({@code PUBLISHED}, {@code PLANNED}) are returned — the state
     * predicate is pushed down to SQL (DB Query Pushdown), never filtered in memory. Overlap
     * condition: {@code wsStart < maintEnd && wsEnd > maintStart}. If {@code maintEnd} is null
     * (indefinite maintenance), all workshops starting after {@code maintStart} match.
     *
     * <p>Task-tailored per ADR 0017: returns only {@code id} + {@code state} (the
     * {@link WorkshopOverlapView}) — the cross-module consumer (FacilityOps impact analysis) needs
     * nothing more, so the adapter selects exactly these two columns (no over-fetch of the UI
     * {@link WorkshopSummaryView}).
     *
     * @param roomId    the room to filter by
     * @param startTime the maintenance window start (inclusive lower bound)
     * @param endTime   the maintenance window end (null = indefinite)
     * @return list of matching workshops as {@link WorkshopOverlapView}
     */
    List<WorkshopOverlapView> getByRoomAndTimeOverlap(UUID roomId, Instant startTime, Instant endTime);

    /**
     * Gets the ids of every {@code PUBLISHED} workshop whose start time has passed ({@code startTime <= now}).
     * Used by the lifecycle scheduler to auto-start workshops that are due (D1). Only the id is returned —
     * the consumer (lifecycle job) only needs the identity to dispatch a {@code StartWorkshopCommand}
     * (consumer-driven, avoids over-fetch). The state and time predicates are pushed down to SQL (DB Query
     * Pushdown), never filtered in memory.
     *
     * @param now the current instant (inclusive upper bound on {@code start_time})
     * @return list of matching workshop ids
     */
    List<WorkshopIdView> getPublishedDueToStart(Instant now);

    /**
     * Gets the ids of every {@code IN_PROGRESS} workshop whose end time has passed ({@code endTime <= now}).
     * Used by the lifecycle scheduler to auto-complete workshops that are due (D2). Only the id is returned —
     * the consumer (lifecycle job) only needs the identity to dispatch a {@code CompleteWorkshopCommand}.
     * The state and time predicates are pushed down to SQL (DB Query Pushdown), never filtered in memory.
     *
     * @param now the current instant (inclusive upper bound on {@code end_time})
     * @return list of lightweight workshop ids
     */
    List<WorkshopIdView> getInProgressDueToComplete(Instant now);

    /**
     * Gets the ids of every {@code PUBLISHED} workshop that is already overdue ({@code endTime < now}).
     * Used by the lifecycle scheduler for the stale catch-up (D3): such a workshop must be rushed through
     * {@code start()} then {@code complete()} within a single transaction. Only the id is returned — the
     * consumer (lifecycle job) only needs the identity to dispatch a {@code CatchUpWorkshopCommand}. The
     * state and time predicates are pushed down to SQL (DB Query Pushdown), never filtered in memory.
     *
     * @param now the current instant (strict upper bound on {@code end_time})
     * @return list of lightweight workshop ids
     */
    List<WorkshopIdView> getPublishedOverdueByEndTime(Instant now);
}
