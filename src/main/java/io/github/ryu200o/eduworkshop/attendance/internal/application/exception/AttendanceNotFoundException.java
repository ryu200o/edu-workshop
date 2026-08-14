package io.github.ryu200o.eduworkshop.attendance.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

/**
 * Application-layer exception raised when an attendance record cannot be found. Thrown by application
 * handlers after an empty port lookup. This is an application concern, not a domain invariant.
 */
public final class AttendanceNotFoundException extends ResourceNotFoundException {

    public AttendanceNotFoundException(java.util.UUID recordId) {
        super("AttendanceRecord", "id", recordId);
    }
}