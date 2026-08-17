-- V19: Add verified_at to registrations (Epic 3C — Registration verification flow).
-- A staff verifier flips REGISTERED → VERIFIED at the door (Registration.verify); the verified_at
-- column records when, for audit clarity (OQ-3C-5) — mirroring the cancelled_at pattern.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE registrations ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE NULL;