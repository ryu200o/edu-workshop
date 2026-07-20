package io.github.ryu200o.eduworkshop.workshop.internal.domain.model;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception.WorkshopDomainException;

import java.util.Objects;

/**
 * Value Object wrapping a workshop's optional description.
 *
 * <p>A description may be absent (null descriptor means "no description"). When present it is trimmed and
 * must not exceed 2000 characters (mirrors {@code @Size(max=2000)} and the {@code VARCHAR(2000)} column).
 * Kept as a VO for symmetry with {@link WorkshopTitle} so future rules can be added without caller churn.</p>
 */
public final class WorkshopDescription {

    private static final int MAX_LENGTH = 2000;

    private final String value;

    private WorkshopDescription(String value) {
        this.value = value;
    }

    /**
     * Wraps a raw description. {@code null}/blank input yields a "no description" instance.
     */
    public static WorkshopDescription of(String raw) {
        if (raw == null || raw.isBlank()) {
            return new WorkshopDescription(null);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new WorkshopDomainException("Workshop description must not exceed " + MAX_LENGTH + " characters.");
        }
        return new WorkshopDescription(trimmed);
    }

    /**
     * Returns the description text, or {@code null} when absent.
     */
    public String asString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof WorkshopDescription that && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value == null ? "<no description>" : value;
    }
}
