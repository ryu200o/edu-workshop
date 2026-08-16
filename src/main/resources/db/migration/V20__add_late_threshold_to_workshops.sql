-- V20: Add the attendance late-policy threshold to workshops (Epic 3C — Workshop owns the
-- attendance policy, ADR 0019 §13.1).
--
-- The threshold is the seconds a learner may check in after start_time and still count as ATTENDED
-- (beyond that = LATE). It is persisted on the workshop row, NOT on the Attendance side: Workshop is
-- the Policy Owner. Chốt OQ-3C-7: range 0..86400 (0-24h), enforced by a DB CHECK as the storage
-- ceiling. Chốt OQ-3C-9: existing rows are backfilled with the create-time default (900s = the
-- historical `app.workshop.checkin.late-after-minutes=15`). The column is seeded at creation from
-- the same config default by the Application handler.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
-- Split into separate statements for H2 compatibility (H2 does not support
-- ADD COLUMN + ADD CONSTRAINT in a single ALTER TABLE).

ALTER TABLE workshops ADD COLUMN late_threshold_seconds INTEGER NOT NULL DEFAULT 900;

ALTER TABLE workshops ADD CONSTRAINT chk_workshops_late_threshold CHECK (late_threshold_seconds BETWEEN 0 AND 86400);