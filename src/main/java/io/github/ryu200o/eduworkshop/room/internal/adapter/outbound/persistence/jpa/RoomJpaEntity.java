package io.github.ryu200o.eduworkshop.room.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a room. Package-private and confined to the outbound persistence adapter —
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

    /**
     * Optimistic-locking version (ADR 0015 Strategy B). Persistence concern only — the domain
     * {@code Room} never carries it. Null on the create path so Spring Data's {@code isNew()}
     * resolves to {@code true} (persist); set by Hibernate on insert and checked/incremented on
     * each update.
     */
    @Version
    private Long version;

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

    void setName(String name) {
        this.name = name;
    }

    String getBuilding() {
        return building;
    }

    void setBuilding(String building) {
        this.building = building;
    }

    int getFloor() {
        return floor;
    }

    void setFloor(int floor) {
        this.floor = floor;
    }

    int getCode() {
        return code;
    }

    void setCode(int code) {
        this.code = code;
    }

    int getCapacity() {
        return capacity;
    }

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    String getState() {
        return state;
    }

    void setState(String state) {
        this.state = state;
    }

    Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
