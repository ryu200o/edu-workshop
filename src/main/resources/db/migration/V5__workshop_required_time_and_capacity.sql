-- V5: Align workshops schema with business semantics — start_time/end_time are required at creation.
-- Business rule: DRAFT always has title, description, start, end, capacity known. Only room is nullable.
-- Portable DDL for H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE workshops ALTER COLUMN start_time SET NOT NULL;
ALTER TABLE workshops ALTER COLUMN end_time SET NOT NULL;

ALTER TABLE workshops DROP CONSTRAINT IF EXISTS chk_workshop_time;
ALTER TABLE workshops ADD CONSTRAINT chk_workshop_time CHECK (end_time > start_time);
