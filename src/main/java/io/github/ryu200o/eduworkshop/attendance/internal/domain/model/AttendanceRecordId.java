package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

import java.util.UUID;

/**
 * Identity value object for an {@link AttendanceRecord} aggregate. Wraps the raw {@code UUID} for
 * compile-time type safety and to keep the Domain model free of primitive obsession.
 */
public record AttendanceRecordId(UUID value) {

    public AttendanceRecordId {
        if (value == null) {
            throw new IllegalArgumentException("AttendanceRecordId must not be null.");
        }
    }

    /**
     * Generates a new attendance-record identity (client-generated, per the module's ID strategy).
     */
    public static AttendanceRecordId generate() {
        return new AttendanceRecordId(UUID.randomUUID());
    }

    public static AttendanceRecordId of(UUID value) {
        return new AttendanceRecordId(value);
    }
}