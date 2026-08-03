-- V13: Add room-eviction flag to workshops (Titik 2).
-- When a room maintenance window is scheduled, overlapping PUBLISHED workshops are flagged
-- with is_room_evicted = true + room_evicted_at (an eviction notice) WITHOUT changing state.
-- The flag is reset (false / null) automatically by changeRoom() / reschedule() in the aggregate.
--
-- NOTE: a filtered index (WHERE is_room_evicted = TRUE) is preferred on PostgreSQL, but H2
-- (test DB) does not support partial/filtered indexes (see ADR 0012). We use a plain index
-- so the DDL stays portable across H2 and PostgreSQL.

ALTER TABLE workshops ADD COLUMN is_room_evicted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE workshops ADD COLUMN room_evicted_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX idx_workshops_evicted ON workshops (is_room_evicted);
