package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.MaintenanceScheduleRepository;
import io.github.ryu200o.eduworkshop.room.internal.application.port.outbound.RoomRepository;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.MaintenanceSchedule;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.Room;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCapacity;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomCode;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomId;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomLocation;
import io.github.ryu200o.eduworkshop.room.internal.domain.model.RoomName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaMaintenanceScheduleWriteAdapterTest {

    @Autowired
    private MaintenanceScheduleRepository maintenanceScheduleRepository;

    @Autowired
    private RoomRepository roomRepository;

    private RoomId roomId;

    @BeforeEach
    void setUp() {
        Room room = Room.create(RoomId.generate(), RoomName.of("F-201"), RoomLocation.of("F", 2),
                RoomCode.of(1), RoomCapacity.of(50), Instant.now());
        Room saved = roomRepository.save(room);
        roomId = saved.id();
    }

    private static final Instant START = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void saveAndLoadById_roundTrip() {
        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), roomId, START, END,
                "Quarterly HVAC filter replacement and duct cleaning", "operator-1", START);

        maintenanceScheduleRepository.save(schedule);

        List<MaintenanceSchedule> found = maintenanceScheduleRepository.findByRoomId(roomId.value());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).id()).isEqualTo(schedule.id());
        assertThat(found.get(0).roomId()).isEqualTo(roomId);
        assertThat(found.get(0).startTime()).isEqualTo(START);
        assertThat(found.get(0).endTime()).isEqualTo(END);
    }

    @Test
    void findOverlapping_returnsCorrectResults() {
        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), roomId, START, END,
                "Quarterly HVAC filter replacement and duct cleaning", "operator-1", START);
        maintenanceScheduleRepository.save(schedule);

        // Overlapping window
        List<MaintenanceSchedule> overlapping = maintenanceScheduleRepository.findOverlapping(
                roomId.value(),
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T14:00:00Z"));
        assertThat(overlapping).hasSize(1);

        // Non-overlapping window
        List<MaintenanceSchedule> nonOverlapping = maintenanceScheduleRepository.findOverlapping(
                roomId.value(),
                Instant.parse("2026-08-02T08:00:00Z"),
                Instant.parse("2026-08-02T12:00:00Z"));
        assertThat(nonOverlapping).isEmpty();
    }

    @Test
    void findOverlapping_indefiniteMaintenance_matchesAll() {
        MaintenanceSchedule indefinite = MaintenanceSchedule.create(
                MaintenanceId.generate(), roomId, START, null,
                "Indefinite maintenance for major renovation project", "operator-1", START);
        maintenanceScheduleRepository.save(indefinite);

        List<MaintenanceSchedule> overlapping = maintenanceScheduleRepository.findOverlapping(
                roomId.value(),
                Instant.parse("2026-08-02T08:00:00Z"),
                Instant.parse("2026-08-02T12:00:00Z"));
        assertThat(overlapping).hasSize(1);
    }

    @Test
    void deleteById_removesSchedule() {
        MaintenanceSchedule schedule = MaintenanceSchedule.create(
                MaintenanceId.generate(), roomId, START, END,
                "Quarterly HVAC filter replacement and duct cleaning", "operator-1", START);
        maintenanceScheduleRepository.save(schedule);

        maintenanceScheduleRepository.deleteById(schedule.id());

        List<MaintenanceSchedule> found = maintenanceScheduleRepository.findByRoomId(roomId.value());
        assertThat(found).isEmpty();
    }
}
