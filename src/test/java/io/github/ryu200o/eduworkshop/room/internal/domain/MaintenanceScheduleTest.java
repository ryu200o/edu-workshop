package io.github.ryu200o.eduworkshop.room.internal.domain;

import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.exception.InvalidMaintenanceScheduleException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceScheduleTest {

    private static final RoomId ROOM_ID = RoomId.of(UUID.randomUUID());
    private static final Instant START = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T12:00:00Z");
    private static final String REASON = "Quarterly HVAC filter replacement and duct cleaning";

    @Test
    void create_validSchedule_succeeds() {
        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, END, REASON, "operator-1", START);

        assertThat(schedule.id()).isNotNull();
        assertThat(schedule.roomId()).isEqualTo(ROOM_ID);
        assertThat(schedule.startTime()).isEqualTo(START);
        assertThat(schedule.endTime()).isEqualTo(END);
        assertThat(schedule.reason()).isEqualTo(REASON);
        assertThat(schedule.createdBy()).isEqualTo("operator-1");
        assertThat(schedule.createdAt()).isEqualTo(START);
        assertThat(schedule.updatedAt()).isEqualTo(START);
    }

    @Test
    void create_reasonTooShort_throws() {
        assertThatThrownBy(() -> MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, END, "short", "operator-1", START))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }

    @Test
    void create_reasonNull_throws() {
        assertThatThrownBy(() -> MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, END, null, "operator-1", START))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }

    @Test
    void create_endTimeBeforeStartTime_throws() {
        Instant badEnd = Instant.parse("2026-07-31T12:00:00Z");

        assertThatThrownBy(() -> MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, badEnd, REASON, "operator-1", START))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }

    @Test
    void create_endTimeEqualsStartTime_throws() {
        assertThatThrownBy(() -> MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, START, REASON, "operator-1", START))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }

    @Test
    void create_endTimeNull_succeeds() {
        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, null, REASON, "operator-1", START);

        assertThat(schedule.endTime()).isNull();
        assertThat(schedule.startTime()).isEqualTo(START);
    }

    @Test
    void create_endTimeInPast_throws() {
        Instant pastEnd = Instant.parse("2026-07-31T12:00:00Z");

        assertThatThrownBy(() -> MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, START, pastEnd, REASON, "operator-1", START))
                .isInstanceOf(InvalidMaintenanceScheduleException.class);
    }

    @Test
    void create_startTimeInPastWithFutureEnd_succeeds() {
        Instant pastStart = Instant.parse("2026-07-31T08:00:00Z");
        Instant futureEnd = Instant.parse("2026-08-31T12:00:00Z");

        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), ROOM_ID, pastStart, futureEnd, REASON, "operator-1", pastStart);

        assertThat(schedule.startTime()).isEqualTo(pastStart);
        assertThat(schedule.endTime()).isEqualTo(futureEnd);
    }

    @Test
    void reconstruct_preservesAllFields() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");

        MaintenanceSchedule schedule = MaintenanceSchedule.reconstruct(
                MaintenanceId.of(UUID.randomUUID()), ROOM_ID, START, END,
                REASON, "operator-1", createdAt, updatedAt);

        assertThat(schedule.createdAt()).isEqualTo(createdAt);
        assertThat(schedule.updatedAt()).isEqualTo(updatedAt);
        assertThat(schedule.startTime()).isEqualTo(START);
        assertThat(schedule.endTime()).isEqualTo(END);
    }
}
