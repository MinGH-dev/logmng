-- External org replica + app_user mapping (req 20260407-external-dept-employee-ad-login)
-- Idempotent for legacy DBs. Fresh installs: objects already in schema_sys.sql (no-op).
-- Apply with: SET search_path TO SCHEMA_SYS, SCHEMA_PB, public;
-- Optional fuzzy name search: enable CREATE EXTENSION pg_trgm in a separate migration and add GIN (display_name gin_trgm_ops); not required for default btree indexes.

CREATE TABLE IF NOT EXISTS ext_department (
    id                      BIGSERIAL PRIMARY KEY,
    source_system           VARCHAR(64) NOT NULL,
    external_department_id  VARCHAR(256) NOT NULL,
    name                    VARCHAR(500) NULL,
    parent_external_department_id VARCHAR(256) NULL,
    imported_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ext_department_source_ext UNIQUE (source_system, external_department_id)
);
CREATE INDEX IF NOT EXISTS idx_ext_department_name ON ext_department (source_system, name);

CREATE TABLE IF NOT EXISTS ext_employee (
    id                      BIGSERIAL PRIMARY KEY,
    source_system           VARCHAR(64) NOT NULL,
    external_employee_id    VARCHAR(256) NOT NULL,
    employee_number         VARCHAR(100) NULL,
    display_name            VARCHAR(500) NULL,
    job_title               VARCHAR(200) NULL,
    external_department_id  VARCHAR(256) NULL,
    email                   VARCHAR(320) NULL,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    imported_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot_id             VARCHAR(128) NULL,
    CONSTRAINT uq_ext_employee_source_ext UNIQUE (source_system, external_employee_id)
);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_empnum ON ext_employee (source_system, employee_number);
CREATE INDEX IF NOT EXISTS idx_ext_employee_display_name ON ext_employee (display_name);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_extdept ON ext_employee (source_system, external_department_id);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_snapshot ON ext_employee (source_system, snapshot_id);

CREATE TABLE IF NOT EXISTS app_user_external_identity (
    id                      BIGSERIAL PRIMARY KEY,
    app_user_id             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_system           VARCHAR(64) NOT NULL,
    external_employee_id    VARCHAR(256) NOT NULL,
    linked_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_external_identity_natural UNIQUE (source_system, external_employee_id),
    CONSTRAINT uq_app_user_external_identity_app_user UNIQUE (app_user_id)
);
CREATE INDEX IF NOT EXISTS idx_app_user_external_identity_app_user ON app_user_external_identity (app_user_id);
