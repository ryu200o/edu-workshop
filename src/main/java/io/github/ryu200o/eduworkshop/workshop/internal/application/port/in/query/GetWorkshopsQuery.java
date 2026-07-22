package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;

import java.util.List;

/**
 * Query to list all workshops. Returns lightweight {@link WorkshopSummaryView} projections.
 * No filters or pagination in this slice.
 */
public record GetWorkshopsQuery() implements Query<List<WorkshopSummaryView>> {
}
