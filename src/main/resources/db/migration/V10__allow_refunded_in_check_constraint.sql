-- V10: Extend the Registration status CHECK constraint to include REFUNDED.
-- The REFUNDED state was added to support system-initiated refunds when a workshop
-- is cancelled (Titik 3: Registration State → REFUNDED).
-- Split into two statements for H2 compatibility (H2 does not support
-- DROP CONSTRAINT + ADD CONSTRAINT in a single ALTER TABLE).
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE registrations DROP CONSTRAINT chk_registrations_status;
ALTER TABLE registrations ADD CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'REFUNDED'));