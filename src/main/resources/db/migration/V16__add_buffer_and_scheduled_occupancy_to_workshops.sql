-- V16: Add Buffer Time & Scheduled Occupancy Window to workshops (Spec v2 / ADR 0018).
-- buffer_before_minutes / buffer_after_minutes: Operational Policy (default 15/15, max 60 in app config).
-- scheduled_occupancy_start / scheduled_occupancy_end: derived denormalized columns (snapshot protects
-- business history, ADR 0018 P2) so JPQL/JOOQ overlap predicates can run in SQL without interval arithmetic.
-- Portable DDL for H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).

ALTER TABLE workshops ADD COLUMN buffer_before_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE workshops ADD COLUMN buffer_after_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE workshops ADD COLUMN scheduled_occupancy_start TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE workshops ADD COLUMN scheduled_occupancy_end TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE workshops ADD CONSTRAINT chk_workshop_buffer_before CHECK (buffer_before_minutes >= 0);
ALTER TABLE workshops ADD CONSTRAINT chk_workshop_buffer_after CHECK (buffer_after_minutes >= 0);
ALTER TABLE workshops ADD CONSTRAINT chk_scheduled_occupancy_time
    CHECK (scheduled_occupancy_end IS NULL OR scheduled_occupancy_end > scheduled_occupancy_start);

CREATE INDEX idx_workshops_scheduled_occupancy
    ON workshops (room_id, scheduled_occupancy_start, scheduled_occupancy_end);
