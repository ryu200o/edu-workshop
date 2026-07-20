package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import java.util.Objects;

/**
 * Value Object wrapping a workshop's title.
 *
 * <p>Enforces the non-blank invariant (RAM self-defense, mirrors {@code @Size(max=200)} and the
 * {@code VARCHAR(200)} column) and normalizes the string (trim, case preserved — human-readable text).
 * The record is retained so future title rules can be added here without touching callers.</p>
 */
public final class WorkshopTitle {

    private static final int MAX_LENGTH = 200;

    private final String value;

    private WorkshopTitle(String value) {
        this.value = value;
    }

    public static WorkshopTitle of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WorkshopDomainException("Workshop title must not be blank.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new WorkshopDomainException("Workshop title must not exceed " + MAX_LENGTH + " characters.");
        }
        return new WorkshopTitle(trimmed);
    }

    public String asString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof WorkshopTitle that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
