package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.GetWorkshopsQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.out.WorkshopReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read handler for {@link GetWorkshopsQuery}. CQRS bypass: reads projections straight from the
 * read port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetWorkshopsQueryHandler implements QueryHandler<GetWorkshopsQuery, List<WorkshopSummaryView>> {

    private final WorkshopReader workshopReader;

    GetWorkshopsQueryHandler(WorkshopReader workshopReader) {
        this.workshopReader = workshopReader;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopSummaryView> handle(GetWorkshopsQuery query) {
        return workshopReader.findAll();
    }
}
