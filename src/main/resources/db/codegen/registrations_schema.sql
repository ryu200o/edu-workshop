-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final registrations schema (V8 + V14). NOT a Flyway migration.
CREATE TABLE registrations (
    id                  UUID                     NOT NULL,
    workshop_id         UUID                     NOT NULL,
    user_id             UUID                     NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    workshop_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    version             BIGINT                   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_registrations PRIMARY KEY (id),
    CONSTRAINT uk_registrations_workshop_user UNIQUE (workshop_id, user_id),
    CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED'))
);
