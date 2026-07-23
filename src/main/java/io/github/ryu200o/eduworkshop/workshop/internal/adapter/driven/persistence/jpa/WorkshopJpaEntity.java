package io.github.ryu200o.eduworkshop.workshop.internal.adapter.driven.persistence.jpa;

import io.github.ryu200o.eduworkshop.workshop.internal.domain.model.WorkshopState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshops")
class WorkshopJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "room_name_snapshot", length = 255)
    private String roomNameSnapshot;

    @Column(name = "room_location_snapshot", length = 255)
    private String roomLocationSnapshot;

    @Column(name = "room_capacity_snapshot")
    private Integer roomCapacitySnapshot;

    @Column(name = "has_room_warning", nullable = false)
    private boolean hasRoomWarning;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkshopState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkshopJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    public UUID getRoomId() {
        return roomId;
    }

    void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public String getRoomNameSnapshot() {
        return roomNameSnapshot;
    }

    void setRoomNameSnapshot(String roomNameSnapshot) {
        this.roomNameSnapshot = roomNameSnapshot;
    }

    public String getRoomLocationSnapshot() {
        return roomLocationSnapshot;
    }

    void setRoomLocationSnapshot(String roomLocationSnapshot) {
        this.roomLocationSnapshot = roomLocationSnapshot;
    }

    public Integer getRoomCapacitySnapshot() {
        return roomCapacitySnapshot;
    }

    void setRoomCapacitySnapshot(Integer roomCapacitySnapshot) {
        this.roomCapacitySnapshot = roomCapacitySnapshot;
    }

    public boolean isHasRoomWarning() {
        return hasRoomWarning;
    }

    void setHasRoomWarning(boolean hasRoomWarning) {
        this.hasRoomWarning = hasRoomWarning;
    }

    public Instant getStartTime() {
        return startTime;
    }

    void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public int getCapacity() {
        return capacity;
    }

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public WorkshopState getState() {
        return state;
    }

    void setState(WorkshopState state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
