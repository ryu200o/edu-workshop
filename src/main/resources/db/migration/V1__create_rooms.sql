-- V1: Create the rooms table (Room module).
-- Portable DDL that runs on both H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
-- Schema aligned to the Room aggregate: name + (building, floor, code) + capacity + state.

CREATE TABLE rooms (
    id         UUID                     NOT NULL,
    name       VARCHAR(50)              NOT NULL,
    building   VARCHAR(20)              NOT NULL,
    floor      INTEGER                  NOT NULL,
    code       VARCHAR(2)               NOT NULL,
    capacity   INTEGER                  NOT NULL,
    state      VARCHAR(20)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_name UNIQUE (name),
    CONSTRAINT uk_rooms_building_floor_code UNIQUE (building, floor, code),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0)
);

-- Composite lookup for the global-uniqueness guard (building + floor + code).
CREATE INDEX idx_rooms_building_floor ON rooms (building, floor);
