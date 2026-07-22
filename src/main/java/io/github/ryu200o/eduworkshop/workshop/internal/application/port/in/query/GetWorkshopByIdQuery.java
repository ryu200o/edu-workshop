package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;

import java.util.UUID;

/**
 * Query to look up a single workshop by its identifier.
 */
public record GetWorkshopByIdQuery(UUID workshopId) implements Query<WorkshopDetailView> {
}
