package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of a ledger entry: {@code (record_id, entry_number)} — the pair that makes
 * the ledger append-only at the schema level (ADR 0019 §6).
 */
@Embeddable
class AttendanceEntryId implements Serializable {

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "entry_number")
    private Integer entryNumber;

    protected AttendanceEntryId() {
        // required by JPA
    }

    AttendanceEntryId(UUID recordId, Integer entryNumber) {
        this.recordId = recordId;
        this.entryNumber = entryNumber;
    }

    UUID getRecordId() {
        return recordId;
    }

    void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    Integer getEntryNumber() {
        return entryNumber;
    }

    void setEntryNumber(Integer entryNumber) {
        this.entryNumber = entryNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttendanceEntryId that)) {
            return false;
        }
        return Objects.equals(recordId, that.recordId) && Objects.equals(entryNumber, that.entryNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, entryNumber);
    }
}