package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

public record ScheduleWorkshopCommand(
        UUID workshopId,
        UUID roomId
) implements Command<ScheduleWorkshopCommand.Result> {

    public record Result(UUID id, UUID roomId, Instant updatedAt, boolean hasRoomWarning) {
    }
}
