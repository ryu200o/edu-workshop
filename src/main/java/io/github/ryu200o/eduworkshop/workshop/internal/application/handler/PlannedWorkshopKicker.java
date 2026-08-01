package io.github.ryu200o.eduworkshop.workshop.internal.application.handler;

import io.github.ryu200o.eduworkshop.workshop.internal.application.port.outbound.WorkshopRepository;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.Workshop;
import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer helper (ADR 0005) that kicks {@code PLANNED} workshops back to {@code DRAFT}
 * when a workshop formally establishes exclusive ownership ({@code PUBLISHED}) over a room's time
 * window. Used by both {@link PublishWorkshopCommandHandler} and
 * {@link ChangeWorkshopRoomCommandHandler}: overlapping {@code PLANNED} workshops are planning-only
 * (ADR 0008) and must not keep believing they hold the room.
 */
@Component
class PlannedWorkshopKicker {

    private final WorkshopRepository workshopRepository;

    PlannedWorkshopKicker(WorkshopRepository workshopRepository) {
        this.workshopRepository = workshopRepository;
    }

    /**
     * Loads all workshops of a room and kicks every other {@code PLANNED} workshop whose time window
     * overlaps the target's back to {@code DRAFT}. Each kicked aggregate is saved and returned so the
     * caller can merge its recorded domain events.
     *
     * @param roomId the room id the target is establishing ownership over
     * @param target the workshop that is being published / moved into the room (never kicked)
     * @param now    the current instant, forwarded to {@code returnToDraft}
     * @return the list of workshops that were kicked back to {@code DRAFT}
     */
    List<Workshop> kickOutOverlappingPlanned(UUID roomId, Workshop target, Instant now) {
        List<Workshop> kickedOut = new ArrayList<>();
        for (Workshop other : workshopRepository.loadByRoomId(roomId)) {
            if (other.id().equals(target.id())) {
                continue;
            }
            if (other.state() != WorkshopState.PLANNED) {
                continue;
            }
            if (!overlaps(other, target)) {
                continue;
            }
            other.returnToDraft(now);
            workshopRepository.save(other);
            kickedOut.add(other);
        }
        return kickedOut;
    }

    private boolean overlaps(Workshop other, Workshop target) {
        return other.startTime().isBefore(target.endTime())
                && target.startTime().isBefore(other.endTime());
    }
}
