package io.github.ryu200o.eduworkshop.room.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ApplicationException;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer exception raised when a maintenance schedule overlaps an existing schedule
 * for the same room (ADR 0005 global/set-based invariant, ADR 0015 Technique 1). Deterministic
 * under a pessimistic write lock: the handler locks the room root row, re-runs {@code findOverlapping}
 * and throws this when a conflict with existing state is detected.
 */
public final class MaintenanceScheduleOverlapException extends ApplicationException {

    public MaintenanceScheduleOverlapException(UUID roomId, Instant startTime, Instant endTime) {
        super("Maintenance schedule [" + startTime + " .. " + (endTime != null ? endTime : "indefinite")
                + "] overlaps an existing maintenance schedule for room " + roomId + ".");
    }
}