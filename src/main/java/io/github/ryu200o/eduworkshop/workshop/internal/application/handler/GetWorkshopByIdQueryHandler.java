package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.exception.WorkshopNotFoundException;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.GetWorkshopByIdQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopDetailView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read handler for {@link GetWorkshopByIdQuery}. CQRS bypass: reads a projection straight from the
 * read port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetWorkshopByIdQueryHandler implements QueryHandler<GetWorkshopByIdQuery, WorkshopDetailView> {

    private final WorkshopReader workshopReader;

    GetWorkshopByIdQueryHandler(WorkshopReader workshopReader) {
        this.workshopReader = workshopReader;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopDetailView handle(GetWorkshopByIdQuery query) {
        return workshopReader.findById(query.workshopId())
                .orElseThrow(() -> new WorkshopNotFoundException("id", query.workshopId()));
    }
}
