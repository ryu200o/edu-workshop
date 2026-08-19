package io.github.ryu200o.eduworkshop.iam.internal.domain.model;

import java.util.Locale;

/**
 * Value object for a user's login email (ADR 0020 §1.1).
 *
 * <p>Invariants: non-blank, a single {@code @} separating a non-empty local part and a non-empty
 * domain part, and no whitespace. The value is normalized to lowercase ({@code LOWER}) at the
 * Domain boundary, matching the {@code CHECK (email = LOWER(email))} + plain unique index on
 * {@code email} used by the persistence layer (portable case-insensitive uniqueness, V21).</p>
 *
 * <p>Per ADR 0009, validation lives here (self-validating VO); the Domain aggregate only null-checks
 * it and never re-checks the business rules.</p>
 */
public record Email(String value) {

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Email must not be blank.");
        }
        if (normalized.indexOf('@') <= 0 || normalized.indexOf('@') != normalized.lastIndexOf('@')) {
            throw new IllegalArgumentException("Email must contain a single '@' with a non-empty local part: " + normalized);
        }
        if (normalized.indexOf('@') == normalized.length() - 1) {
            throw new IllegalArgumentException("Email must have a non-empty domain part: " + normalized);
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Email must not contain whitespace: " + normalized);
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }
}