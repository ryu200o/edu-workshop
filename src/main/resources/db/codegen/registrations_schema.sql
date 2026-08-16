-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final registrations schema (V8 + V10 + V11 + V14 + V15 + V18 + V19). NOT a Flyway migration.
CREATE TABLE registrations (
    id                  UUID                     NOT NULL,
    workshop_id         UUID                     NOT NULL,
    user_id             UUID                     NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    workshop_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    workshop_title_snapshot   VARCHAR(200),
    workshop_end_time_snapshot TIMESTAMP WITH TIME ZONE,
    workshop_room_name_snapshot VARCHAR(255),
    registered_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    verified_at         TIMESTAMP WITH TIME ZONE,
    grace_period_until  TIMESTAMP WITH TIME ZONE,
    version             BIGINT                   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_registrations PRIMARY KEY (id),
    CONSTRAINT uk_registrations_workshop_user UNIQUE (workshop_id, user_id),
    CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'REFUNDED', 'VERIFIED'))
);
