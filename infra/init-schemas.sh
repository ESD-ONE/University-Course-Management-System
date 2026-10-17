#!/bin/bash
# One shared PostgreSQL instance and database ("ucms"), one schema per service —
# see docs/architecture/DATA_GOVERNANCE.md "The created_by / updated_by FK rule"
# for why, and docs/ARCHITECTURE_ANALYSIS.md for the trade-off against a separate
# database per service.
#
# Isolation is enforced by giving each service its own login role, granted
# privileges on its own schema only — a service's credentials simply have no
# grants outside its own schema. Runs once, against an empty data directory,
# via docker-entrypoint-initdb.d; IDENTITY_ACCESS_SVC_PASSWORD comes from this
# container's own environment so the password never lives in a committed file.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    -- Extensions are database-scoped, not schema-scoped, and schema-scoped
    -- service roles below don't have CREATE EXTENSION privilege (correctly —
    -- that's a superuser-adjacent operation). Create it once here instead of
    -- in each service's own Flyway migration.
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    CREATE SCHEMA IF NOT EXISTS identity_access;
    CREATE SCHEMA IF NOT EXISTS academic;
    CREATE SCHEMA IF NOT EXISTS enrollment;
    CREATE SCHEMA IF NOT EXISTS assignment_grading;
    CREATE SCHEMA IF NOT EXISTS notification;
    CREATE SCHEMA IF NOT EXISTS analytics;
    CREATE SCHEMA IF NOT EXISTS reference_data;

    -- Only Identity & Access is implemented so far — its role is created and
    -- scoped now; each further service gets its own role (same pattern) added
    -- here as it's built, rather than all 7 pre-provisioned with unused passwords.
    CREATE ROLE identity_access_svc LOGIN PASSWORD '${IDENTITY_ACCESS_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA identity_access TO identity_access_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA identity_access
        GRANT ALL PRIVILEGES ON TABLES TO identity_access_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA identity_access
        GRANT ALL PRIVILEGES ON SEQUENCES TO identity_access_svc;
SQL
