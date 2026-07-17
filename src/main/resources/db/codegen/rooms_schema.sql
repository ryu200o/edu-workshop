-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final rooms schema (post V1 + V2) so code generation does not depend on
-- H2-incompatible ALTER statements. NOT a Flyway migration.
CREATE TABLE rooms (
    id         UUID                     NOT NULL,
    name       VARCHAR(50)              NOT NULL,
    building   VARCHAR(20)              NOT NULL,
    floor      INTEGER                  NOT NULL,
    code       VARCHAR(10)              NOT NULL,
    capacity   INTEGER                  NOT NULL,
    state      VARCHAR(20)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_name UNIQUE (name),
    CONSTRAINT uk_rooms_building_floor_code UNIQUE (building, floor, code),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_rooms_building_floor ON rooms (building, floor);
