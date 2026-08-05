package io.github.ryu200o.eduworkshop.registration.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for a registration. Package-private and confined to the outbound persistence
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

    @Column(name = "grace_period_until")
    private Instant gracePeriodUntil;

    /**
     * Optimistic-locking version (ADR 0015 Strategy B). Persistence concern only — the domain
     * {@code Registration} never carries it. Null on the create path so Spring Data's
     * {@code isNew()} resolves to {@code true} (persist); set by Hibernate on insert and
     * checked/incremented on each update.
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RegistrationJpaEntity() {
        // required by JPA
    }

    RegistrationJpaEntity(UUID id, UUID workshopId, UUID userId, String status, Instant workshopStartTime,
                          Instant registeredAt, Instant cancelledAt, Instant gracePeriodUntil, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.workshopId = workshopId;
        this.userId = userId;
        this.status = status;
        this.workshopStartTime = workshopStartTime;
        this.registeredAt = registeredAt;
        this.cancelledAt = cancelledAt;
        this.gracePeriodUntil = gracePeriodUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getWorkshopId() {
        return workshopId;
    }

    void setWorkshopId(UUID workshopId) {
        this.workshopId = workshopId;
    }

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Instant getWorkshopStartTime() {
        return workshopStartTime;
    }

    void setWorkshopStartTime(Instant workshopStartTime) {
        this.workshopStartTime = workshopStartTime;
    }

    Instant getRegisteredAt() {
        return registeredAt;
    }

    void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    Instant getCancelledAt() {
        return cancelledAt;
    }

    void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    Instant getGracePeriodUntil() {
        return gracePeriodUntil;
    }

    void setGracePeriodUntil(Instant gracePeriodUntil) {
        this.gracePeriodUntil = gracePeriodUntil;
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
