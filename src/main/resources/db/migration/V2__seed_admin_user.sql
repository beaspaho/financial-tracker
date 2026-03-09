-- ============================================================
-- V2__seed_admin_user.sql
-- Seeds a placeholder ADMIN user record.
--
-- PURPOSE:
--   The actual Keycloak identity for the admin is created by the
--   realm-export.json (imported on Keycloak startup via Docker Compose).
--   This row creates the corresponding LOCAL profile in our DB so that
--   AdminService queries work from the first boot.
--
-- The keycloak_id here ('00000000-0000-0000-0000-000000000001') is a
-- placeholder — it MUST match the "id" of the admin user in realm-export.json.
--
-- NOTE: In a real production deployment, admin users are provisioned
-- through the admin registration API or a secure bootstrap script,
-- not via a Flyway migration. This is assessment-appropriate shorthand.
-- ============================================================

INSERT INTO fn_trn_users (id, keycloak_id, email, role, created_at, updated_at, created_by)
VALUES (
           gen_random_uuid(),
           '00000000-0000-0000-0000-000000000001',   -- matches realm-export.json admin user id
           'admin@example.com',
           'ADMIN',
           NOW(),
           NOW(),
           'system'
       )
    ON CONFLICT (keycloak_id) DO NOTHING;   -- idempotent — safe to re-run