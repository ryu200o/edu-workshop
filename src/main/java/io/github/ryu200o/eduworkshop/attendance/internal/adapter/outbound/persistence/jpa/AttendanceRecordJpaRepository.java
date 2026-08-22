package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link AttendanceRecordJpaEntity}. Method names are read-side filters
 * used by the write adapter; the read side goes through JOOQ (CQRS bypass).
 */
interface AttendanceRecordJpaRepository extends JpaRepository<AttendanceRecordJpaEntity, UUID> {

    List<AttendanceRecordJpaEntity> findByWorkshopIdAndStudentId(UUID workshopId, UUID studentId);

    List<AttendanceRecordJpaEntity> findByWorkshopIdAndState(UUID workshopId, String state);

    List<AttendanceRecordJpaEntity> findByWorkshopIdAndStateNot(UUID workshopId, String state);

    List<AttendanceRecordJpaEntity> findByWorkshopId(UUID workshopId);

    @Query("SELECT DISTINCT a.workshopId FROM AttendanceRecordJpaEntity a WHERE a.state <> :state")
    List<UUID> findDistinctWorkshopIdByStateNot(String state);
}