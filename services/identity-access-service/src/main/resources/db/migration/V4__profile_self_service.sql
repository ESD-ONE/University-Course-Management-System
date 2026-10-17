-- Backs the Profile screen's self-service name edit, password change, and
-- notification preferences (html-wireframes/index.html profile(), lines
-- 2199-2234). Preferences are stored here because they're the account's own
-- settings, not Notification-service content — but nothing consumes them
-- yet, since no Notification service exists. See
-- docs/FULL_SYSTEM_GAP_ANALYSIS.md section D.

ALTER TABLE users
    ADD COLUMN notify_email boolean NOT NULL DEFAULT true,
    ADD COLUMN notify_assignments boolean NOT NULL DEFAULT true,
    ADD COLUMN notify_grades boolean NOT NULL DEFAULT true,
    ADD COLUMN notify_registrations boolean NOT NULL DEFAULT true;
