-- V16: Add the end_time page of the Occupancy Window to workshops (ADR 0018 v2 / Spec v3,
-- Selective Occupancy Denormalization).
--
-- A workspace occupies the room from `occupancy_start` (start_time minus the applied System
-- Buffer) until `end_time`. Only the LEFT bound is denormalized: `occupancy_end ≡ end_time`, so
-- the existing `end_time` column doubles as the right bound — no `occupancy_end` column exists.
-- Overlap checks are native predicates on these two columns (DB Query Pushdown, ADR 0016):
--
--   WHERE room_id = :roomId
--     AND state IN ('PUBLISHED', 'PLANNED')
--     AND end_time      > :targetOccupancyStart
--     AND occupancy_start < :targetEndTime
--
-- supported by the composite B-Tree index (room_id, occupancy_start, end_time). There is NO
-- `buffer_before_minutes` column anymore (v1 removed) and no storage-ceiling CHECK — the buffer
-- value only feeds the pure function `occupancy_start = start_time - currentConfigBuffer` once at
-- scheduling time and is not persisted. Portable DDL: runs on both H2 (PostgreSQL mode, tests)
-- and PostgreSQL.

ALTER TABLE workshops ADD COLUMN occupancy_start TIMESTAMP WITH TIME ZONE NOT NULL;

CREATE INDEX idx_workshops_room_occupancy ON workshops (room_id, occupancy_start, end_time);