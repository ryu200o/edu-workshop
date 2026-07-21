-- V4: Create the Workshop module tables (workshops / workshop_histories / workshop_snapshots).
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
--
-- Schema decisions (see docs/db/database.md §3-5 and the workshop ADR discussion):
--  * room_id + room_name_snapshot + room_location_snapshot are NULLABLE: a DRAFT workshop may exist
--    before a room is assigned. The domain aggregate enforces they are non-null before PUBLISHED.
--  * chk_workshop_time is NULL-aware so an unscheduled DRAFT passes (end_time/start_time may be null).
--  * idx_workshop_room_time is a partial index (room assigned, not CANCELLED) for the overlap scan.
--  * workshop_histories / workshop_snapshots use a physical FK to workshops(id) WITH ON DELETE CASCADE:
--    these are same-module tables, so a cross-module boundary is NOT crossed.

CREATE TABLE workshops (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),

    room_id UUID,
    room_name_snapshot VARCHAR(255),
    room_location_snapshot VARCHAR(255),

    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    capacity INT NOT NULL CHECK (capacity > 0),
    state VARCHAR(50) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_workshop_time CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time)
);

CREATE INDEX idx_workshop_room_id ON workshops (room_id);
CREATE INDEX idx_workshop_state ON workshops (state);
CREATE INDEX idx_workshop_start_time ON workshops (start_time);
-- NOTE: a partial index (WHERE room_id IS NOT NULL AND state != 'CANCELLED') is preferred on
-- PostgreSQL for the overlap scan, but H2 (test DB) does not support filtered indexes. We use a
-- plain composite index so the DDL stays portable across H2 and PostgreSQL; the same query predicate
-- still benefits from the leading (room_id, start_time, end_time) columns.
CREATE INDEX idx_workshop_room_time ON workshops (room_id, start_time, end_time);

CREATE TABLE workshop_histories (
    id UUID PRIMARY KEY,
    workshop_id UUID NOT NULL REFERENCES workshops (id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    reason VARCHAR(255),
    changed_by UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workshop_histories_workshop_id ON workshop_histories (workshop_id);
CREATE INDEX idx_workshop_histories_occurred_at ON workshop_histories (occurred_at);
CREATE INDEX idx_workshop_histories_type ON workshop_histories (event_type);
-- NOTE: on PostgreSQL a GIN index is preferred for JSONB containment queries, but H2 (test DB)
-- only supports BTREE/HASH/RTREE, so we use a plain index to keep the DDL portable.
CREATE INDEX idx_workshop_histories_data ON workshop_histories (event_data);

CREATE TABLE workshop_snapshots (
    id UUID PRIMARY KEY,
    workshop_id UUID NOT NULL REFERENCES workshops (id) ON DELETE CASCADE,
    room_name VARCHAR(255) NOT NULL,
    room_location VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INT NOT NULL,
    actual_attendance INT DEFAULT 0,
    feedback_score DECIMAL(3, 2),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_workshop_snapshot_workshop ON workshop_snapshots (workshop_id);
CREATE INDEX idx_workshop_snapshot_completed_at ON workshop_snapshots (completed_at);
