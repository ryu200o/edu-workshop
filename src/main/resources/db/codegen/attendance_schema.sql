-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final attendance schema (V17). NOT a Flyway migration.
CREATE TABLE attendance_records (
    id                        UUID                     NOT NULL,
    student_id                UUID                     NOT NULL,
    workshop_id               UUID                     NOT NULL,
    current_result            VARCHAR(20)              NOT NULL,
    state                     VARCHAR(20)              NOT NULL,
    reconciliation_started_at TIMESTAMP WITH TIME ZONE,
    version                   BIGINT                   NOT NULL DEFAULT 0,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_attendance_records PRIMARY KEY (id),
    CONSTRAINT uq_student_workshop UNIQUE (student_id, workshop_id)
);

CREATE TABLE attendance_entries (
    record_id         UUID                     NOT NULL,
    entry_number      INT                      NOT NULL,
    timestamp         TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id          UUID                     NOT NULL,
    actor_role        VARCHAR(20)              NOT NULL,
    action            VARCHAR(30)              NOT NULL,
    result            VARCHAR(20)              NOT NULL,
    reason            TEXT,
    evidence_reference TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_attendance_entries PRIMARY KEY (record_id, entry_number),
    CONSTRAINT fk_entries_record FOREIGN KEY (record_id)
        REFERENCES attendance_records(id) ON DELETE RESTRICT
);