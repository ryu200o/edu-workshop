-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final workshop schema (post V4) so code generation does not depend on
-- H2-incompatible ALTER statements. NOT a Flyway migration.
CREATE TABLE workshops (
    id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    room_id UUID,
    room_name_snapshot VARCHAR(255),
    room_location_snapshot VARCHAR(255),
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    capacity INTEGER NOT NULL,
    state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshops PRIMARY KEY (id),
    CONSTRAINT chk_workshop_time CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time),
    CONSTRAINT chk_workshop_capacity CHECK (capacity > 0)
);

CREATE TABLE workshop_histories (
    id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    reason VARCHAR(255),
    changed_by UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshop_histories PRIMARY KEY (id)
);

CREATE TABLE workshop_snapshots (
    id UUID NOT NULL,
    workshop_id UUID NOT NULL,
    room_name VARCHAR(255) NOT NULL,
    room_location VARCHAR(255) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL,
    actual_attendance INTEGER,
    feedback_score DECIMAL(3, 2),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workshop_snapshots PRIMARY KEY (id),
    CONSTRAINT uk_workshop_snapshot_workshop UNIQUE (workshop_id)
);
