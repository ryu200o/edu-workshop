package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsInProgressDueToCompleteQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopSummaryView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read handler for {@link GetWorkshopsInProgressDueToCompleteQuery}. CQRS bypass: reads projections
 * straight from the read port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetWorkshopsInProgressDueToCompleteQueryHandler
        implements QueryHandler<GetWorkshopsInProgressDueToCompleteQuery, List<WorkshopSummaryView>> {

    private final WorkshopReader workshopReader;

    GetWorkshopsInProgressDueToCompleteQueryHandler(WorkshopReader workshopReader) {
        this.workshopReader = workshopReader;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopSummaryView> handle(GetWorkshopsInProgressDueToCompleteQuery query) {
        return workshopReader.getInProgressDueToComplete(query.now());
    }
}