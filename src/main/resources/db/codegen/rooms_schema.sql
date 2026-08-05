-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final rooms schema (post V1 + V2 + V12 + V14) so code generation does not depend on
-- H2-incompatible ALTER statements. NOT a Flyway migration.
CREATE TABLE rooms (
    id         UUID                     NOT NULL,
    name       VARCHAR(50)              NOT NULL,
    building   VARCHAR(20)              NOT NULL,
    floor      INTEGER                  NOT NULL,
    code       VARCHAR(10)              NOT NULL,
    capacity   INTEGER                  NOT NULL,
    state      VARCHAR(20)              NOT NULL,
    version    BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_name UNIQUE (name),
    CONSTRAINT uk_rooms_building_floor_code UNIQUE (building, floor, code),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_rooms_building_floor ON rooms (building, floor);

CREATE TABLE room_maintenance_schedules (
    id          UUID                     NOT NULL,
    room_id     UUID                     NOT NULL,
    start_time  TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time    TIMESTAMP WITH TIME ZONE NULL,
    reason      VARCHAR(500)             NOT NULL,
    created_by  VARCHAR(100)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_room_maintenance_schedules PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT chk_maintenance_time CHECK (end_time IS NULL OR end_time > start_time),
    CONSTRAINT chk_maintenance_reason CHECK (CHAR_LENGTH(reason) >= 10)
);

CREATE INDEX idx_maintenance_room_id ON room_maintenance_schedules(room_id);
CREATE INDEX idx_maintenance_time_window ON room_maintenance_schedules(start_time, end_time);
