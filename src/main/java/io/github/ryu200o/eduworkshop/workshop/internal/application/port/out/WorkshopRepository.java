package io.github.ryu200o.eduworkshop.workshop.internal.application.port.out;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.util.Optional;

public interface WorkshopRepository {

    Workshop save(Workshop workshop);

    Optional<Workshop> loadById(WorkshopId id);
}
