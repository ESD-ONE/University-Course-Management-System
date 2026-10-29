-- Notification Service schema — database "ucms", schema "notification".
-- Matches docs/architecture/tables-05-notification.mmd, plus:
--   * notifications.source_event_id — the RabbitMQ event that produced it. Unique per
--     (event, user) so a redelivered message can't create a duplicate notification.
--   * notifications.category — which preference toggle governs it.
--   * notification_preferences.user_id is UNIQUE (one row per user).

CREATE TABLE notifications (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid          NOT NULL,
    type             varchar(60)   NOT NULL,
    category         varchar(20)   NOT NULL CHECK (category IN ('REGISTRATION','ASSIGNMENT','GRADE','SYSTEM')),
    title            varchar(200)  NOT NULL,
    body             text          NOT NULL,
    link             varchar(300),
    source_event_id  uuid,
    read_at          timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_per_event_user UNIQUE (source_event_id, user_id)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid        NOT NULL UNIQUE,
    email_enabled        boolean     NOT NULL DEFAULT true,
    assignment_alerts    boolean     NOT NULL DEFAULT true,
    grade_alerts         boolean     NOT NULL DEFAULT true,
    registration_alerts  boolean     NOT NULL DEFAULT true,
    version              integer     NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid
);
