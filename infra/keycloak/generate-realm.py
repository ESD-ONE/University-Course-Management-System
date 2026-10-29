#!/usr/bin/env python3
"""Generates realm/ucms-realm.json — the ucms realm Keycloak imports on first start.

Edit this script, not the JSON (the user-profile config is JSON embedded as a string
inside the realm JSON, which is unpleasant to edit by hand), then re-run:

    python3 infra/keycloak/generate-realm.py

Secrets are ${PLACEHOLDERS} that Keycloak resolves from its own environment at import
time, so the committed file holds no credentials. Note: Keycloak imports a realm only if
it doesn't exist yet — see infra/keycloak/README.md for applying changes to a running one.
"""
import json
import pathlib

# Which services each backend service may call, from the *_SERVICE_URI settings in each
# service's application.yml. A caller needs the "svc:<callee>" realm role; the callee
# accepts a service token only if it carries its own role.
SERVICES = {
    # client id                 short name     may call
    "svc-identity-access":      ("identity",   []),
    "svc-base":                 ("base",       ["academic"]),
    "svc-academic-structure":   ("academic",   ["identity"]),
    "svc-enrollment":           ("enrollment", ["academic", "identity"]),
    "svc-assignment-grading":   ("grading",    ["academic", "enrollment", "base", "identity"]),
    "svc-notification":         ("notification", ["enrollment", "identity"]),
    "svc-analytics-reporting":  ("analytics",  ["identity", "academic", "enrollment", "grading"]),
}

# Keep in step with harden_realm.py, which applies the same list to an existing realm.
WEB_ORIGINS = [
    "http://localhost:4200",          # ng serve
    "http://localhost:8091",          # the web container, run locally
    "https://esd.azhosting.fun",      # production domain (the only public way in)
]

USER_PROFILE = {
    "attributes": [
        {"name": "username", "displayName": "${username}",
         "validations": {"length": {"min": 3, "max": 255}, "username-prohibited-characters": {},
                         "up-username-not-idn-homograph": {}},
         "permissions": {"view": ["admin", "user"], "edit": ["admin"]}, "multivalued": False},
        {"name": "email", "displayName": "${email}",
         "validations": {"email": {}, "length": {"max": 255}},
         "permissions": {"view": ["admin", "user"], "edit": ["admin"]}, "multivalued": False},
        # Not required: identity-access-service stores one "name" field, which it splits
        # into these; making them required would send users with a one-word name to an
        # "update your profile" page on login.
        {"name": "firstName", "displayName": "${firstName}",
         "validations": {"length": {"max": 255}, "person-name-prohibited-characters": {}},
         "permissions": {"view": ["admin", "user"], "edit": ["admin"]}, "multivalued": False},
        {"name": "lastName", "displayName": "${lastName}",
         "validations": {"length": {"max": 255}, "person-name-prohibited-characters": {}},
         "permissions": {"view": ["admin", "user"], "edit": ["admin"]}, "multivalued": False},
        # The account's id in identity-access-service, put in every user token as "uid".
        # Admin-only: users can neither see nor change it.
        {"name": "ucms_uid", "displayName": "UCMS user id",
         "permissions": {"view": ["admin"], "edit": ["admin"]}, "multivalued": False},
    ],
    "groups": [{"name": "user-metadata", "displayHeader": "User metadata",
                "displayDescription": "Attributes, which refer to user metadata"}],
}


def env(name):
    return "${" + name + "}"


def secret_env(client_id):
    return "KC_" + client_id.upper().replace("-", "_") + "_SECRET"


def service_client(client_id):
    client = {
        "clientId": client_id,
        "name": client_id,
        "enabled": True,
        "publicClient": False,
        "clientAuthenticatorType": "client-secret",
        "secret": env(secret_env(client_id)),
        "standardFlowEnabled": False,
        "implicitFlowEnabled": False,
        "directAccessGrantsEnabled": False,
        "serviceAccountsEnabled": True,
        "protocol": "openid-connect",
        "attributes": {"access.token.lifespan": "300"},
    }
    if client_id == "svc-identity-access":
        # Used only server-side, to check a user's current password before changing it.
        client["directAccessGrantsEnabled"] = True
    return client


def service_account(client_id):
    short, calls = SERVICES[client_id]
    account = {
        "username": "service-account-" + client_id,
        "enabled": True,
        "serviceAccountClientId": client_id,
        "realmRoles": ["service"] + ["svc:" + c for c in calls],
    }
    if client_id == "svc-identity-access":
        # Mirrors account changes made on the Users screen into Keycloak.
        account["clientRoles"] = {"realm-management": ["manage-users", "view-users", "query-users"]}
    return account


web_client = {
    "clientId": "ucms-web",
    "name": "UCMS web app",
    "enabled": True,
    "publicClient": True,
    "standardFlowEnabled": True,
    "implicitFlowEnabled": False,
    "directAccessGrantsEnabled": False,
    "serviceAccountsEnabled": False,
    "protocol": "openid-connect",
    "redirectUris": [o + "/*" for o in WEB_ORIGINS],
    "webOrigins": ["+"],
    "attributes": {
        "pkce.code.challenge.method": "S256",
        "post.logout.redirect.uris": "##".join(o + "/*" for o in WEB_ORIGINS),
    },
    "protocolMappers": [{
        "name": "ucms user id",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-attribute-mapper",
        "consentRequired": False,
        "config": {
            "user.attribute": "ucms_uid",
            "claim.name": "uid",
            "jsonType.label": "String",
            "access.token.claim": "true",
            "id.token.claim": "true",
            "userinfo.token.claim": "true",
            "introspection.token.claim": "true",
        },
    }],
}

realm = {
    "realm": "ucms",
    "displayName": "UCMS",
    "displayNameHtml": "<b>UCMS</b> &middot; University Course Management System",
    "enabled": True,
    "sslRequired": "external",
    "registrationAllowed": False,
    "resetPasswordAllowed": False,
    "rememberMe": False,
    "loginWithEmailAllowed": True,
    "duplicateEmailsAllowed": False,
    "editUsernameAllowed": False,
    "verifyEmail": False,
    # Short-lived access tokens, silently refreshed by the browser; the SSO session caps a
    # sign-in at 24 hours, after which the user has to log in again.
    "accessTokenLifespan": 300,
    "ssoSessionIdleTimeout": 86400,
    "ssoSessionMaxLifespan": 86400,
    "bruteForceProtected": True,
    "failureFactor": 10,
    "permanentLockout": False,
    "maxFailureWaitSeconds": 900,
    "waitIncrementSeconds": 60,
    # Existing realms don't pick changes here up (import skips a realm that exists):
    # apply them with harden_realm.py. Refresh-token rotation is deliberately off — every
    # browser tab shares one client session, so rotating would sign the other tabs out.
    "passwordPolicy": "length(12) and maxLength(128) and notUsername and notEmail and passwordHistory(3)",
    "eventsEnabled": True,
    "eventsExpiration": 2592000,
    "eventsListeners": ["jboss-logging"],
    "adminEventsEnabled": True,
    "adminEventsDetailsEnabled": False,
    "roles": {
        "realm": [
            {"name": "ADMIN", "description": "UCMS administrator"},
            {"name": "LECTURER", "description": "UCMS lecturer"},
            {"name": "STUDENT", "description": "UCMS student"},
            {"name": "service", "description": "Marks a backend service's own service account"},
        ] + [
            {"name": "svc:" + short, "description": f"May call the {short} service as a trusted service"}
            for short, _ in SERVICES.values()
        ],
    },
    "clients": [web_client] + [service_client(c) for c in SERVICES],
    "users": [service_account(c) for c in SERVICES],
    "components": {
        "org.keycloak.userprofile.UserProfileProvider": [{
            "providerId": "declarative-user-profile",
            "subComponents": {},
            "config": {"kc.user.profile.config": [json.dumps(USER_PROFILE)]},
        }],
    },
}

out = pathlib.Path(__file__).parent / "realm" / "ucms-realm.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps(realm, indent=2) + "\n")
print(f"wrote {out}")
