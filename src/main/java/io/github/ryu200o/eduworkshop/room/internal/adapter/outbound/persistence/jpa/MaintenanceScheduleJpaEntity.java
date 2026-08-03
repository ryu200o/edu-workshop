package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for the {@code room_maintenance_schedules} table. Package-private and
 * confined to the outbound persistence adapter — it is an infrastructure detail, entirely separate
 * from the framework-free domain {@code MaintenanceSchedule}.
 */
@Entity
@Table(name = "room_maintenance_schedules")
class MaintenanceScheduleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenanceScheduleJpaEntity() {
        // required by JPA
    }

    MaintenanceScheduleJpaEntity(UUID id, UUID roomId, Instant startTime, Instant endTime,
                                  String reason, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getRoomId() {
        return roomId;
    }

    Instant getStartTime() {
        return startTime;
    }

    Instant getEndTime() {
        return endTime;
    }

    String getReason() {
        return reason;
    }

    String getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
