package io.github.ryu200o.eduworkshop.room.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for a {@link MaintenanceSchedule}. Wraps the raw {@code UUID} to gain
 * compile-time type safety and keep the Domain model free of primitive obsession.
 */
public record MaintenanceId(UUID value) {

    public MaintenanceId {
        if (value == null) {
            throw new IllegalArgumentException("MaintenanceId must not be null.");
        }
    }

    public static MaintenanceId generate() {
        return new MaintenanceId(UUID.randomUUID());
    }

    public static MaintenanceId of(UUID value) {
        return new MaintenanceId(value);
    }
}
