package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

/**
 * Value object for a workshop's title. Invariants: non-blank, at most 200 characters.
 */
public record WorkshopTitle(String value) {

    private static final int MAX_LENGTH = 200;

    public WorkshopTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Workshop title must not be blank.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Workshop title must not exceed " + MAX_LENGTH + " characters.");
        }
    }

    public static WorkshopTitle of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Workshop title must not be blank.");
        }
        return new WorkshopTitle(raw.trim());
    }
}
