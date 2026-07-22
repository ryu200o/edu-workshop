package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight read projection (View) summarizing a workshop for list contexts. Lives on the Query
 * side; allowed to grow or shrink independently of the write flow (CQRS bypass).
 *
 * @param id        the workshop id
 * @param title     the workshop title
 * @param startTime the planned start time
 * @param endTime   the planned end time
 * @param state     the lifecycle state (DRAFT / SCHEDULED / PUBLISHED / …)
 */
public record WorkshopSummaryView(
        UUID id,
        String title,
        Instant startTime,
        Instant endTime,
        String state
) {
}
