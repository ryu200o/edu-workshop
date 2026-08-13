package io.github.ryu200o.eduworkshop.attendance.internal.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AttendanceEntryJpaEntity} (the Decision Ledger rows). Insert-only by design:
 * the write adapter never updates or deletes entries (ADR 0019 §6) — it only calls {@code saveAll}
 * for brand-new ledger entries.
 */
interface AttendanceEntryJpaRepository extends JpaRepository<AttendanceEntryJpaEntity, AttendanceEntryId> {
}