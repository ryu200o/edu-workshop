package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import java.util.Objects;

/**
 * Value object for a workshop's description. Optional (nullable). Invariant: at most 2000 characters
 * when present.
 */
public record WorkshopDescription(String value) {

    private static final int MAX_LENGTH = 2000;

    public WorkshopDescription {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Workshop description must not exceed " + MAX_LENGTH + " characters.");
        }
    }

    public static WorkshopDescription of(String raw) {
        if (raw == null || raw.isBlank()) {
            return new WorkshopDescription(null);
        }
        return new WorkshopDescription(raw.trim());
    }
}
