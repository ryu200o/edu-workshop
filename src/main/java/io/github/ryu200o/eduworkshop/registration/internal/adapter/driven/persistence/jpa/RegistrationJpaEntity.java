package io.github.ryu200o.eduworkshop.registration.internal.adapter.driven.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a registration. Package-private and confined to the driven persistence
 * adapter — it is an infrastructure detail, entirely separate from the framework-free domain
 * {@code Registration}.
 */
@Entity
@Table(name = "registrations")
class RegistrationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "workshop_start_time", nullable = false)
    private Instant workshopStartTime;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RegistrationJpaEntity() {
        // required by JPA
    }

    RegistrationJpaEntity(UUID id, UUID workshopId, UUID userId, String status, Instant workshopStartTime,
                          Instant registeredAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.workshopId = workshopId;
        this.userId = userId;
        this.status = status;
        this.workshopStartTime = workshopStartTime;
        this.registeredAt = registeredAt;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkshopId() {
        return workshopId;
    }

    UUID getUserId() {
        return userId;
    }

    String getStatus() {
        return status;
    }

    Instant getWorkshopStartTime() {
        return workshopStartTime;
    }

    Instant getRegisteredAt() {
        return registeredAt;
    }

    Instant getCancelledAt() {
        return cancelledAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
