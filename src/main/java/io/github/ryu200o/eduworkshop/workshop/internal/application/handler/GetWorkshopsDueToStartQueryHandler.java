package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.QueryHandler;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.GetWorkshopsDueToStartQuery;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.inbound.query.view.WorkshopIdView;
import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read handler for {@link GetWorkshopsDueToStartQuery}. CQRS bypass: reads workshop ids straight from
 * the read port, no domain involvement. Package-private; side-effect free.
 */
@Component
class GetWorkshopsDueToStartQueryHandler
        implements QueryHandler<GetWorkshopsDueToStartQuery, List<WorkshopIdView>> {

    private final WorkshopReader workshopReader;

    GetWorkshopsDueToStartQueryHandler(WorkshopReader workshopReader) {
        this.workshopReader = workshopReader;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopIdView> handle(GetWorkshopsDueToStartQuery query) {
        return workshopReader.getPublishedDueToStart(query.now());
    }
}