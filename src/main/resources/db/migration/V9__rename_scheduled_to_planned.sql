-- V9: Rename the pre-publish lifecycle state SCHEDULED → PLANNED (Ubiquitous Language refactor).
-- Business rule: the pre-publish stage is *planning* (no exclusive room reservation — ADR 0008);
-- "Reschedule" (Phase 3) is reserved for changing a PUBLISHED workshop's time.
-- Portable UPDATE that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
-- The `state` column is a free-form VARCHAR(50) with no CHECK on the value list, so a plain
-- data UPDATE suffices — no column ALTER, no enum migration needed.

UPDATE workshops SET state = 'PLANNED' WHERE state = 'SCHEDULED';
