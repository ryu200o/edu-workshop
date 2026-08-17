-- =============================================================================
-- Migration: V21__create_iam_tables.sql
-- Module: Identity & Access Management (IAM)
-- Target: PostgreSQL (+ H2 for tests)
-- Description: Core tables for IAM aggregate with strict CHECK constraints,
--              case-insensitive email uniqueness, and secure bootstrap admin seed.
-- Portability note: functional index `UNIQUE ... (LOWER(email))` is NOT portable to
-- H2 (test DB). Instead: CHECK `email = LOWER(email)` (enforces lowercase storage on
-- both engines) + plain UNIQUE index on `email` -> case-insensitive uniqueness is
-- preserved race-proof at DB level (same spirit as ADR 0011 V7 portability tweaks).
-- =============================================================================

-- 1. Bảng Aggregate Root User (Credentials + Profile + Security State)
CREATE TABLE iam_users (
                           id                      UUID PRIMARY KEY,
                           email                   VARCHAR(255) NOT NULL,
                           password_hash           VARCHAR(255) NOT NULL,
                           status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
                           full_name               VARCHAR(100) NOT NULL,
                           phone_number            VARCHAR(20),
                           student_code            VARCHAR(30),
                           avatar_url              VARCHAR(255),
                           must_change_password    BOOLEAN NOT NULL DEFAULT FALSE,
                           failed_login_attempts   INT NOT NULL DEFAULT 0,
                           lockout_count           INT NOT NULL DEFAULT 0,
                           locked_until            TIMESTAMP WITH TIME ZONE,
                           last_locked_at          TIMESTAMP WITH TIME ZONE,
                           created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
                           updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
version                 BIGINT NOT NULL DEFAULT 0,
                            CONSTRAINT chk_iam_users_status CHECK (
                                status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED')
                                ),
                            CONSTRAINT chk_iam_users_email_lowercase CHECK (email = LOWER(email)),
                            CONSTRAINT uk_iam_users_student_code UNIQUE (student_code)
);

-- Case-insensitive email uniqueness: CHECK forces lowercase storage; the plain UNIQUE
-- index on `email` then enforces uniqueness race-proof on both H2 and PostgreSQL.
CREATE UNIQUE INDEX uk_iam_users_email_lower ON iam_users (email);
CREATE INDEX idx_iam_users_status ON iam_users (status);

-- 2. Bảng Global Roles của User (USER là base role bắt buộc)
CREATE TABLE iam_user_roles (
                                user_id     UUID NOT NULL,
                                role        VARCHAR(32) NOT NULL,
                                created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT pk_iam_user_roles PRIMARY KEY (user_id, role),
                                CONSTRAINT fk_iam_user_roles_user FOREIGN KEY (user_id)
                                    REFERENCES iam_users(id) ON DELETE CASCADE,
                                CONSTRAINT chk_iam_user_roles_role CHECK (
                                    role IN ('USER', 'ADMIN', 'PLANNER', 'AUDITOR', 'VERIFIER')
                                    )
);

CREATE INDEX idx_iam_user_roles_role ON iam_user_roles (role);

-- 3. Bảng Quản lý Refresh Token (Stateful Session & Rotation)
CREATE TABLE iam_refresh_tokens (
                                    id          UUID PRIMARY KEY,
                                    user_id     UUID NOT NULL,
                                    token_hash  VARCHAR(255) NOT NULL,
                                    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                    revoked_at  TIMESTAMP WITH TIME ZONE,
                                    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                    CONSTRAINT uk_iam_refresh_tokens_hash UNIQUE (token_hash),
                                    CONSTRAINT fk_iam_refresh_tokens_user FOREIGN KEY (user_id)
                                        REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_iam_refresh_tokens_user_id ON iam_refresh_tokens (user_id);
CREATE INDEX idx_iam_refresh_tokens_lookup ON iam_refresh_tokens (token_hash, revoked_at, expires_at);

-- 4. Bảng Password Reset Tokens (Single-use OTP/Token Hash)
CREATE TABLE iam_password_reset_tokens (
                                           id          UUID PRIMARY KEY,
                                           user_id     UUID NOT NULL,
                                           token_hash  VARCHAR(255) NOT NULL,
                                           expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                           used_at     TIMESTAMP WITH TIME ZONE,
                                           created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                           CONSTRAINT uk_iam_reset_tokens_hash UNIQUE (token_hash),
                                           CONSTRAINT fk_iam_reset_tokens_user FOREIGN KEY (user_id)
                                               REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_iam_reset_tokens_lookup ON iam_password_reset_tokens (token_hash, used_at, expires_at);

-- 5. Seed Bootstrap Super Admin
-- Ghi chú vận hành: Mật khẩu ban đầu được quản lý qua kênh bảo mật nội bộ và bắt buộc đổi ngay trong lần đầu đăng nhập.
INSERT INTO iam_users (
    id,
    email,
    password_hash,
    status,
    full_name,
    must_change_password,
    failed_login_attempts,
    lockout_count,
    created_at,
    updated_at,
    version
) VALUES (
             '00000000-0000-0000-0000-000000000001',
             'admin@eduworkshop.local',
             '$2a$12$e8Yk1O1aZ9rV9bLqLp6.euYp7wM2EwOq3FjT5iT5e.K1B3bK8o3Gy',
             'ACTIVE',
             'System Administrator',
             TRUE,
             0,
             0,
             CURRENT_TIMESTAMP,
             CURRENT_TIMESTAMP,
             0
         );

INSERT INTO iam_user_roles (user_id, role) VALUES
                                               ('00000000-0000-0000-0000-000000000001', 'USER'),
                                               ('00000000-0000-0000-0000-000000000001', 'ADMIN');