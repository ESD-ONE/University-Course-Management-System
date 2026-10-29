-- Analytics & Reporting Service schema — database "ucms", schema "analytics".
-- Matches docs/architecture/tables-06-analytics-reporting.mmd, plus:
--   * dashboard_snapshots.metric_value is text holding JSON (charts are lists, not a
--     single scalar), and (role, metric_key) is unique — a snapshot is overwritten in
--     place on each recompute.
--   * reports.object_key — where the last generated file sits in MinIO.
--   * scheduled_reports.next_run_at — when the scheduler should run it next.

CREATE TABLE dashboard_snapshots (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role          varchar(20)  NOT NULL,
    metric_key    varchar(120) NOT NULL,
    metric_value  text         NOT NULL,
    computed_at   timestamptz  NOT NULL,
    CONSTRAINT uq_snapshot_role_key UNIQUE (role, metric_key)
);

CREATE TABLE reports (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name               varchar(120) NOT NULL UNIQUE,
    description        varchar(300) NOT NULL,
    format             varchar(10)  NOT NULL CHECK (format IN ('CSV','PDF')),
    last_generated_at  timestamptz,
    object_key         varchar(300),
    version            integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         uuid
);

CREATE TABLE scheduled_reports (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id    uuid         NOT NULL REFERENCES reports(id),
    frequency    varchar(40)  NOT NULL,
    recipients   varchar(1000) NOT NULL,
    status       varchar(20)  NOT NULL CHECK (status IN ('ACTIVE','PAUSED')),
    next_run_at  timestamptz,
    version      integer      NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   uuid,
    deleted_at   timestamptz,
    deleted_by   uuid
);

CREATE INDEX idx_scheduled_reports_due ON scheduled_reports (next_run_at) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
