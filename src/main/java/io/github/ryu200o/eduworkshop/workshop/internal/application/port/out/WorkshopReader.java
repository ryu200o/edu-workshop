package io.github.ryu200o.eduworkshop.workshop.internal.application.port.out;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;

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
}
