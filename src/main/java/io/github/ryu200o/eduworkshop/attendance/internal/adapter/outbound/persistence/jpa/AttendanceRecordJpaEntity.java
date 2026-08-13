package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA persistence model for the Attendance Record master (ADR 0019 §6). Holds the materialized
 * current state ({@code currentResult}, {@code state}) and the reconciliation anchor
 * ({@code reconciliationStartedAt}) plus the version column for optimistic locking (ADR 0015).
 *
 * <p>The ledger entries are an {@code EAGER} child collection keyed by the composite
 * {@code (record_id, entry_number)} and <em>append-only</em>. The collection is read-only from JPA's
 * perspective (no cascade): new entries are inserted explicitly by the write adapter, so existing
 * rows are never merged, updated or deleted.</p>
 */
@Entity
@Table(name = "attendance_records")
class AttendanceRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "current_result", nullable = false, length = 20)
    private String currentResult;

    @Column(name = "state", nullable = false, length = 20)
    private String state;

    @Column(name = "reconciliation_started_at")
    private Instant reconciliationStartedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "record", fetch = FetchType.EAGER)
    @OrderBy("id.entryNumber ASC")
    private List<AttendanceEntryJpaEntity> entries = new ArrayList<>();

    protected AttendanceRecordJpaEntity() {
        // required by JPA
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getStudentId() {
        return studentId;
    }

    void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    UUID getWorkshopId() {
        return workshopId;
    }

    void setWorkshopId(UUID workshopId) {
        this.workshopId = workshopId;
    }

    String getCurrentResult() {
        return currentResult;
    }

    void setCurrentResult(String currentResult) {
        this.currentResult = currentResult;
    }

    String getState() {
        return state;
    }

    void setState(String state) {
        this.state = state;
    }

    Instant getReconciliationStartedAt() {
        return reconciliationStartedAt;
    }

    void setReconciliationStartedAt(Instant reconciliationStartedAt) {
        this.reconciliationStartedAt = reconciliationStartedAt;
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

    List<AttendanceEntryJpaEntity> getEntries() {
        return entries;
    }

    void setEntries(List<AttendanceEntryJpaEntity> entries) {
        this.entries = entries;
    }
}