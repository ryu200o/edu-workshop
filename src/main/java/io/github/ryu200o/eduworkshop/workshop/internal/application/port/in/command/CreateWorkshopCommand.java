package io.github.ryu200o.eduworkshop.workshop.internal.application.port.in.command;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Command;

import java.time.Instant;
import java.util.UUID;

public record CreateWorkshopCommand(
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        int capacity
) implements Command<CreateWorkshopCommand.Result> {

    public record Result(UUID id, String title) {
    }
}
