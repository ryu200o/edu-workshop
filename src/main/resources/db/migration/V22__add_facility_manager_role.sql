-- Extend the global-role CHECK on iam_user_roles to include FACILITY_MANAGER (ADR 0023, Slice 1).
-- The GlobalRole enum gained FACILITY_MANAGER, but the DB CHECK constraint must allow it too,
-- otherwise granting the role raises DataIntegrityViolationException (mapped to 500 "conflict").
ALTER TABLE iam_user_roles DROP CONSTRAINT chk_iam_user_roles_role;
ALTER TABLE iam_user_roles ADD CONSTRAINT chk_iam_user_roles_role
    CHECK (role IN ('USER', 'ADMIN', 'PLANNER', 'AUDITOR', 'VERIFIER', 'FACILITY_MANAGER'));
