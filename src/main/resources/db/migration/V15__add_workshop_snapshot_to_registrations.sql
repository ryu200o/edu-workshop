-- V15: Selectively snapshot Workshop display data onto registrations (ADR 0007).
-- Lets the learner "My Bookings" read side be a single self-contained SELECT on the
-- registrations table — no cross-module JOIN and no in-memory filter (DB Query Pushdown).
-- These columns are refreshed at register/reactivate and on workshop reschedule, exactly
-- like the existing workshop_start_time snapshot.
-- Backfill decision (Epic 2 spec §5.2): historical rows keep NULL snapshots; the read view
-- falls back to empty strings. No SQL backfill is performed.
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE registrations ADD COLUMN workshop_title_snapshot VARCHAR(200);
ALTER TABLE registrations ADD COLUMN workshop_end_time_snapshot TIMESTAMP WITH TIME ZONE;
ALTER TABLE registrations ADD COLUMN workshop_room_name_snapshot VARCHAR(255);
