-- Keycloak now owns sign-in (see docs/keycloak-integration.md). Each UCMS account is
-- mirrored to a Keycloak user, and this records which one. Keycloak assigns its own ids,
-- so the link is stored here; Keycloak in turn holds this account's id in its "ucms_uid"
-- attribute, which every user token carries as the "uid" claim.
--
-- NULL means "not yet linked": KeycloakReconciler links (or creates) the Keycloak user,
-- which is how existing accounts are migrated — with their BCrypt password hash, so
-- nobody has to reset a password.
ALTER TABLE users ADD COLUMN keycloak_id UUID;
CREATE UNIQUE INDEX users_keycloak_id_key ON users (keycloak_id);
