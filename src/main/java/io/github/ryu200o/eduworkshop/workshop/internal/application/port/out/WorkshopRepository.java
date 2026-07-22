package io.github.ryu200o.eduworkshop.workshop.internal.application.port.out;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopId;

import java.util.Optional;

/**
 * Outbound port (SPI) for persisting and loading Workshop aggregates on the write side.
 * Implemented by a driven adapter ({@code JpaWorkshopWriteAdapter}). The save operation
 * wraps database constraint violations into {@code WorkshopPersistenceException}.
 */
public interface WorkshopRepository {

    Workshop save(Workshop workshop);

    Optional<Workshop> loadById(WorkshopId id);
}
