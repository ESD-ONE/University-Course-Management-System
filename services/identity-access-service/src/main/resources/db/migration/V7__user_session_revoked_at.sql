-- G-11: supports a real (if partial) logout. revokedAt marks the session record revoked
-- for audit/visibility purposes only — this does NOT invalidate the JWT itself; see the
-- Javadoc on AuthController.logout() for why. A single nullable timestamp, rather than
-- the full soft-delete/version pattern other entities carry via Auditable, since
-- user_sessions otherwise has no audit columns at all (see V1's comment on this table).
-- See docs/gap-analysis-report.md G-11.

ALTER TABLE user_sessions ADD COLUMN revoked_at timestamptz;
