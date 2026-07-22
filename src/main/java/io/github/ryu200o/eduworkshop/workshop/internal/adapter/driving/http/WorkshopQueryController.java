package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driving.http;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryBus;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.GetWorkshopByIdQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.GetWorkshopsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Driving HTTP adapter for the Workshop READ side (Query). Accepts only data-reading HTTP methods (GET)
 * and talks exclusively to the shared {@link QueryBus}. Package-private and confined to the module's
 * internal boundary. Error handling is centralized in {@link WorkshopExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/workshops")
class WorkshopQueryController {

    private final QueryBus queryBus;

    WorkshopQueryController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/{id}")
    WorkshopDetailView getById(@PathVariable UUID id) {
        var query = new GetWorkshopByIdQuery(id);
        return queryBus.execute(query);
    }

    @GetMapping
    List<WorkshopSummaryView> getAll() {
        var query = new GetWorkshopsQuery();
        return queryBus.execute(query);
    }
}
