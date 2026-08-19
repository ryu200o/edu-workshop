-- Codegen-only DDL for JOOQ (DDLDatabase).
-- Mirrors the final iam schema (V21). NOT a Flyway migration.
-- NOTE: V21 defines the email uniqueness as a functional index (LOWER(email)); H2's DDL simulation
-- cannot parse functional indexes, so it is mirrored here as a plain unique index on `email`.
-- The generated JOOQ model only needs the column metadata; the runtime DB keeps the functional index.
CREATE TABLE iam_users (
    id                    UUID                     NOT NULL,
    email                 VARCHAR(255)             NOT NULL,
    password_hash         VARCHAR(255)             NOT NULL,
    status                VARCHAR(32)              NOT NULL,
    full_name             VARCHAR(100)             NOT NULL,
    phone_number          VARCHAR(20),
    student_code          VARCHAR(30),
    avatar_url            VARCHAR(255),
    must_change_password  BOOLEAN                  NOT NULL,
    failed_login_attempts INTEGER                  NOT NULL,
    lockout_count         INTEGER                  NOT NULL,
    locked_until          TIMESTAMP WITH TIME ZONE,
    last_locked_at        TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT                   NOT NULL,
    CONSTRAINT pk_iam_users PRIMARY KEY (id),
    CONSTRAINT uk_iam_users_student_code UNIQUE (student_code),
    CONSTRAINT chk_iam_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_iam_users_email_lower ON iam_users (email);

CREATE INDEX idx_iam_users_status ON iam_users (status);

CREATE TABLE iam_user_roles (
    user_id    UUID                     NOT NULL,
    role       VARCHAR(32)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_iam_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_iam_user_roles_user FOREIGN KEY (user_id)
        REFERENCES iam_users(id) ON DELETE CASCADE,
    CONSTRAINT chk_iam_user_roles_role CHECK (role IN ('USER', 'ADMIN', 'PLANNER', 'AUDITOR', 'VERIFIER'))
);

CREATE INDEX idx_iam_user_roles_role ON iam_user_roles (role);

CREATE TABLE iam_refresh_tokens (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    token_hash VARCHAR(255)             NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_iam_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_iam_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_iam_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_iam_refresh_tokens_user_id ON iam_refresh_tokens (user_id);

CREATE INDEX idx_iam_refresh_tokens_lookup ON iam_refresh_tokens (token_hash, revoked_at, expires_at);

CREATE TABLE iam_password_reset_tokens (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    token_hash VARCHAR(255)             NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_iam_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_iam_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_iam_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_iam_reset_tokens_lookup ON iam_password_reset_tokens (token_hash, used_at, expires_at);