package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for one Decision Ledger entry. <strong>Append-only:</strong> the entry is
 * only ever inserted — it is never updated, merged or removed (no orphan-removal, no REMOVE cascade;
 * the aggregate never exposes removal). The composite key {@code (record_id, entry_number)} is the
 * append-only gate at the schema level (ADR 0019 §6).
 */
@Entity
@Table(name = "attendance_entries")
class AttendanceEntryJpaEntity {

    @EmbeddedId
    private AttendanceEntryId id;

    @MapsId("recordId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id")
    private AttendanceRecordJpaEntity record;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "reason")
    private String reason;

    @Column(name = "evidence_reference")
    private String evidenceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AttendanceEntryJpaEntity() {
        // required by JPA
    }

    AttendanceEntryId getId() {
        return id;
    }

    void setId(AttendanceEntryId id) {
        this.id = id;
    }

    AttendanceRecordJpaEntity getRecord() {
        return record;
    }

    void setRecord(AttendanceRecordJpaEntity record) {
        this.record = record;
    }

    Instant getTimestamp() {
        return timestamp;
    }

    void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    UUID getActorId() {
        return actorId;
    }

    void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    String getActorRole() {
        return actorRole;
    }

    void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    String getAction() {
        return action;
    }

    void setAction(String action) {
        this.action = action;
    }

    String getResult() {
        return result;
    }

    void setResult(String result) {
        this.result = result;
    }

    String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    String getEvidenceReference() {
        return evidenceReference;
    }

    void setEvidenceReference(String evidenceReference) {
        this.evidenceReference = evidenceReference;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}