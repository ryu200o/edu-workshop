package io.github.ryu200o.eduworkshop.attendance.internal.application.mapper;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;

/**
 * Pure mapper between the Workshop module's attendance evaluation contract and the Attendance
 * domain result. The Workshop module owns the policy and returns {@code ATTENDED | LATE}; the
 * Attendance module translates it into its own {@link AttendanceResult} before writing the ledger.
 * No other mapping needed — no business rules live here.
 */
public final class AttendanceMapper {

    private AttendanceMapper() {
    }

    public static AttendanceResult toResult(AttendanceStatusContract status) {
        return switch (status) {
            case ATTENDED -> AttendanceResult.PRESENT;
            case LATE -> AttendanceResult.LATE;
        };
    }
}
