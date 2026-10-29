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
    -- Renamed from reference_data (infrastructure rename only, see
    -- docs/reference-data-vs-base-analysis.md and docs/implementation-change-log.md).
    -- This script only runs once against an empty data directory — it does NOT rename
    -- an already-provisioned schema on a live database. See the change log for the
    -- manual ALTER SCHEMA/ALTER ROLE step required before deploying this rename to an
    -- environment that already has data under the old names.
    CREATE SCHEMA IF NOT EXISTS base;

    -- Each implemented service gets its own role (same pattern), added here as
    -- it's built, rather than all 7 pre-provisioned with unused passwords.
    CREATE ROLE identity_access_svc LOGIN PASSWORD '${IDENTITY_ACCESS_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA identity_access TO identity_access_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA identity_access
        GRANT ALL PRIVILEGES ON TABLES TO identity_access_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA identity_access
        GRANT ALL PRIVILEGES ON SEQUENCES TO identity_access_svc;

    CREATE ROLE base_svc LOGIN PASSWORD '${BASE_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA base TO base_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA base
        GRANT ALL PRIVILEGES ON TABLES TO base_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA base
        GRANT ALL PRIVILEGES ON SEQUENCES TO base_svc;

    CREATE ROLE academic_structure_svc LOGIN PASSWORD '${ACADEMIC_STRUCTURE_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA academic TO academic_structure_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA academic
        GRANT ALL PRIVILEGES ON TABLES TO academic_structure_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA academic
        GRANT ALL PRIVILEGES ON SEQUENCES TO academic_structure_svc;

    CREATE ROLE enrollment_svc LOGIN PASSWORD '${ENROLLMENT_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA enrollment TO enrollment_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA enrollment
        GRANT ALL PRIVILEGES ON TABLES TO enrollment_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA enrollment
        GRANT ALL PRIVILEGES ON SEQUENCES TO enrollment_svc;

    CREATE ROLE assignment_grading_svc LOGIN PASSWORD '${ASSIGNMENT_GRADING_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA assignment_grading TO assignment_grading_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA assignment_grading
        GRANT ALL PRIVILEGES ON TABLES TO assignment_grading_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA assignment_grading
        GRANT ALL PRIVILEGES ON SEQUENCES TO assignment_grading_svc;

    CREATE ROLE notification_svc LOGIN PASSWORD '${NOTIFICATION_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA notification TO notification_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA notification
        GRANT ALL PRIVILEGES ON TABLES TO notification_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA notification
        GRANT ALL PRIVILEGES ON SEQUENCES TO notification_svc;

    CREATE ROLE analytics_svc LOGIN PASSWORD '${ANALYTICS_SVC_PASSWORD}';
    GRANT USAGE, CREATE ON SCHEMA analytics TO analytics_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA analytics
        GRANT ALL PRIVILEGES ON TABLES TO analytics_svc;
    ALTER DEFAULT PRIVILEGES IN SCHEMA analytics
        GRANT ALL PRIVILEGES ON SEQUENCES TO analytics_svc;

    -- Keycloak (the identity provider — see docs/keycloak-integration.md) keeps its own
    -- tables in its own schema, created and migrated by Keycloak itself, so its role
    -- owns that schema outright.
    CREATE SCHEMA IF NOT EXISTS keycloak;
    CREATE ROLE keycloak_svc LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
    ALTER SCHEMA keycloak OWNER TO keycloak_svc;
    ALTER ROLE keycloak_svc SET search_path = keycloak;
SQL
