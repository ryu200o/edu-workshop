package io.github.ryu200o.eduworkshop.attendance.internal.application.mapper;

import io.github.ryu200o.eduworkshop.attendance.internal.domain.model.AttendanceResult;
import io.github.ryu200o.eduworkshop.workshop.contract.AttendanceStatusContract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceMapperTest {

    @Test
    void mapsAttendedToPresent() {
        assertThat(AttendanceMapper.toResult(AttendanceStatusContract.ATTENDED))
                .isEqualTo(AttendanceResult.PRESENT);
    }

    @Test
    void mapsLateToLate() {
        assertThat(AttendanceMapper.toResult(AttendanceStatusContract.LATE))
                .isEqualTo(AttendanceResult.LATE);
    }
}
