-- V18: Extend the Registration status CHECK constraint to include VERIFIED.
-- The VERIFIED state is added per the SA directive (Registration Verification Dependency):
-- the Attendance module only records attendance for learners whose seat is VERIFIED.
-- No REGISTERED → VERIFIED transition exists yet (dedicated Registration Verification task);
-- this migration merely widens the allowed status values so seeded/future VERIFIED rows are valid.
-- Split into two statements for H2 compatibility (H2 does not support
-- DROP CONSTRAINT + ADD CONSTRAINT in a single ALTER TABLE).
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE registrations DROP CONSTRAINT chk_registrations_status;
ALTER TABLE registrations ADD CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'REFUNDED', 'VERIFIED'));