-- =============================================================================
-- Migration: V17__create_attendance_tables.sql
-- Description: Core Attendance Record Aggregate & Append-Only Entries Ledger
-- =============================================================================
-- ADR 0019 (REVISED v2): Attendance Record = append-only Decision Ledger.
--  * attendance_records is the MASTER: one row per (workshop, student), holding the
--    materialized current state (current_result) + reconciliation anchor
--    (reconciliation_started_at = WorkshopCompleted.completedAt) + optimistic-lock
--    version (ADR 0015 Strategy B, nullable on insert path → 409 on OOLF).
--  * attendance_entries is the LEDGER: append-only history of decisions
--    (MARK / APPEAL / AUDITOR_ADJUST / FINALIZE). Never updated, never deleted.
--    FK entries→records is ON DELETE RESTRICT — the ledger cannot outlive the master,
--    and a record can never be deleted while entries reference it.
--  * Append-only is additionally enforced in the JPA mapping (insert-only, no
--    REMOVE / orphan-removal) and by the application never exposing UPDATE/DELETE.
--  * Portable DDL: H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
-- =============================================================================

CREATE TABLE attendance_records (
    id                       UUID                     PRIMARY KEY,
    student_id               UUID                     NOT NULL,
    workshop_id              UUID                     NOT NULL,
    current_result           VARCHAR(20)              NOT NULL,  -- 'PRESENT','LATE','ABSENT','EXCUSED'
    state                    VARCHAR(20)              NOT NULL,  -- 'OPEN','RECONCILING','FINALIZED'
    reconciliation_started_at TIMESTAMP WITH TIME ZONE,          -- = WorkshopCompleted.completedAt
    version                  BIGINT                   NOT NULL DEFAULT 0,  -- optimistic lock (ADR 0015)
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_student_workshop UNIQUE (student_id, workshop_id),
    CONSTRAINT chk_attendance_records_state  CHECK (state IN ('OPEN', 'RECONCILING', 'FINALIZED')),
    CONSTRAINT chk_attendance_records_result CHECK (current_result IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED'))
);

CREATE TABLE attendance_entries (
    record_id        UUID                     NOT NULL,
    entry_number     INT                      NOT NULL,
    timestamp        TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id         UUID                     NOT NULL,
    actor_role       VARCHAR(20)              NOT NULL,  -- 'TRAINER','STUDENT','AUDITOR','SYSTEM'
    action           VARCHAR(30)              NOT NULL,  -- 'MARK','APPEAL','AUDITOR_ADJUST','FINALIZE'
    result           VARCHAR(20)              NOT NULL,  -- result state after this entry
    reason           TEXT,
    evidence_reference TEXT,                              -- URL, QR hash, camera log id, or JSON ref
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_attendance_entries PRIMARY KEY (record_id, entry_number),
    CONSTRAINT fk_entries_record FOREIGN KEY (record_id)
        REFERENCES attendance_records(id) ON DELETE RESTRICT,
    CONSTRAINT chk_attendance_entries_action CHECK (action IN ('MARK', 'APPEAL', 'AUDITOR_ADJUST', 'FINALIZE')),
    CONSTRAINT chk_attendance_entries_role   CHECK (actor_role IN ('TRAINER', 'STUDENT', 'AUDITOR', 'SYSTEM')),
    CONSTRAINT chk_attendance_entries_result CHECK (result IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED'))
);

-- Indexes for Quick View & Roster Queries
CREATE INDEX idx_att_records_workshop_result
    ON attendance_records (workshop_id, current_result);

CREATE INDEX idx_att_records_student
    ON attendance_records (student_id);

-- Index for Audit & Ledger Retrieval (redundant with PK, kept for clarity of intent)
CREATE INDEX idx_att_entries_record_seq
    ON attendance_entries (record_id, entry_number ASC);
