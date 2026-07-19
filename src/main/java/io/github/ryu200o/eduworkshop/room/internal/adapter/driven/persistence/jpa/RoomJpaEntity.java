package io.github.ryu200o.eduworkshop.room.internal.adapter.driven.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a room. Package-private and confined to the driven persistence adapter —
 * it is an infrastructure detail, entirely separate from the framework-free domain {@code Room}.
 */
@Entity
@Table(name = "rooms")
class RoomJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String building;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false)
    private int code;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomJpaEntity() {
        // required by JPA
    }

    RoomJpaEntity(UUID id, String name, String building, int floor, int code,
                  int capacity, String state, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.building = building;
        this.floor = floor;
        this.code = code;
        this.capacity = capacity;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getBuilding() {
        return building;
    }

    int getFloor() {
        return floor;
    }

    int getCode() {
        return code;
    }

    int getCapacity() {
        return capacity;
    }

    String getState() {
        return state;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
