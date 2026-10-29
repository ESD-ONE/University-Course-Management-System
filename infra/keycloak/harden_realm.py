#!/usr/bin/env python3
"""Applies the ucms realm's security settings to a Keycloak that already has the realm.

generate-realm.py's JSON is only imported when the realm doesn't exist yet, so a running
Keycloak (local or production) needs these applied once through the admin API:

  * password policy: 12-128 characters, not the username/email, not one of the last 3
  * login and admin event logging (30 days)
  * the web client's allowed redirect origins (the plain-HTTP server address is gone:
    the app is only served over HTTPS at the domain)
  * optionally, a one-time-password second factor for ADMIN accounts (--admin-mfa on)

Usage (production: run on the host, or through the SSH tunnel to 127.0.0.1:8180):
    KEYCLOAK_ADMIN_PASSWORD=... python3 infra/keycloak/harden_realm.py
    KEYCLOAK_ADMIN_PASSWORD=... python3 infra/keycloak/harden_realm.py --admin-mfa on
    KEYCLOAK_ADMIN_PASSWORD=... python3 infra/keycloak/harden_realm.py --admin-mfa off

Admin MFA: the realm's browser login switches to a copy of Keycloak's "browser" flow whose
OTP step is required for users holding the ADMIN realm role (instead of "only if they've
set one up"). An administrator without an authenticator yet is walked through setting one
up at their next sign-in. Lecturers and students are unaffected. "off" switches the realm
back to the stock flow (the copy is left in place, unused; "on" always rebuilds it).

Only Python's standard library is used. Safe to re-run.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

REALM = "ucms"
MFA_FLOW = "browser-admin-otp"
PASSWORD_POLICY = "length(12) and maxLength(128) and notUsername and notEmail and passwordHistory(3)"
# Same list as generate-realm.py.
WEB_ORIGINS = ["http://localhost:4200", "http://localhost:8091", "https://esd.azhosting.fun"]
EVENTS = {
    "eventsEnabled": True,
    "eventsExpiration": 2592000,
    "eventsListeners": ["jboss-logging"],
    "enabledEventTypes": [],  # empty = every type
    "adminEventsEnabled": True,
    "adminEventsDetailsEnabled": False,
}


class Admin:
    def __init__(self, base, password):
        self.base = base.rstrip("/")
        form = urllib.parse.urlencode({
            "grant_type": "password", "client_id": "admin-cli",
            "username": os.environ.get("KEYCLOAK_ADMIN_USER", "admin"), "password": password,
        }).encode()
        with urllib.request.urlopen(f"{self.base}/realms/master/protocol/openid-connect/token", form) as r:
            self.token = json.load(r)["access_token"]

    def call(self, method, path, body=None, ok=(200, 201, 204)):
        req = urllib.request.Request(
            f"{self.base}/admin/realms/{REALM}{path}", method=method,
            data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as r:
                raw = r.read()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            if e.code in ok:
                return e.code, None
            raise SystemExit(f"{method} {path} -> {e.code}: {e.read().decode(errors='replace')}")


def harden(admin):
    admin.call("PUT", "", {"passwordPolicy": PASSWORD_POLICY})
    admin.call("PUT", "/events/config", EVENTS)
    print(f"password policy: {PASSWORD_POLICY}")
    print("login + admin events: on, kept 30 days")
    web = admin.call("GET", "/clients?clientId=ucms-web")[1][0]
    web["redirectUris"] = [o + "/*" for o in WEB_ORIGINS]
    web.setdefault("attributes", {})["post.logout.redirect.uris"] = "##".join(o + "/*" for o in WEB_ORIGINS)
    admin.call("PUT", f"/clients/{web['id']}", web)
    print("web client redirect origins: " + ", ".join(WEB_ORIGINS))


def executions(admin):
    return admin.call("GET", f"/authentication/flows/{MFA_FLOW}/executions")[1]


def admin_mfa_on(admin):
    # Rebuilt from Keycloak's stock "browser" flow every time, so re-running repairs a copy
    # that was edited by hand (or by an older version of this script).
    admin.call("PUT", "", {"browserFlow": "browser"})
    for flow in admin.call("GET", "/authentication/flows")[1]:
        if flow["alias"] == MFA_FLOW:
            admin.call("DELETE", f"/authentication/flows/{flow['id']}")
    admin.call("POST", "/authentication/flows/browser/copy", {"newName": MFA_FLOW})

    # The subflow holding the OTP form (Keycloak 26 also has a "Conditional Organization"
    # subflow, so match on what's inside, not on the name).
    steps = executions(admin)
    otp = next(e for e in steps if e.get("providerId") == "auth-otp-form")
    parent = next(e for e in reversed(steps[:steps.index(otp)])
                  if e.get("authenticationFlow") and e["level"] == otp["level"] - 1)
    siblings = [e for e in steps[steps.index(parent) + 1:steps.index(otp) + 1] if e["level"] == otp["level"]]

    for e in siblings:
        if e.get("providerId") == "conditional-user-configured":
            admin.call("DELETE", f"/authentication/executions/{e['id']}")

    admin.call("POST", f"/authentication/flows/{urllib.parse.quote(parent['displayName'])}/executions/execution",
               {"provider": "conditional-user-role"})
    role_step = next(e for e in executions(admin) if e.get("providerId") == "conditional-user-role"
                     and e["level"] == otp["level"])
    role_step["requirement"] = "REQUIRED"
    admin.call("PUT", f"/authentication/flows/{MFA_FLOW}/executions", role_step)
    admin.call("POST", f"/authentication/executions/{role_step['id']}/config",
               {"alias": "ucms-admins", "config": {"condUserRole": "ADMIN", "negate": "false"}})

    admin.call("PUT", "", {"browserFlow": MFA_FLOW})
    print(f"admin MFA: on (browser flow = {MFA_FLOW}; ADMIN accounts must use an authenticator app)")


def admin_mfa_off(admin):
    admin.call("PUT", "", {"browserFlow": "browser"})
    print("admin MFA: off (browser flow = browser)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default=os.environ.get("KEYCLOAK_ADMIN_URL", "http://localhost:8180/auth"))
    ap.add_argument("--admin-mfa", choices=["on", "off"])
    args = ap.parse_args()
    password = os.environ.get("KEYCLOAK_ADMIN_PASSWORD")
    if not password:
        sys.exit("Set KEYCLOAK_ADMIN_PASSWORD (the value in infra/.env).")
    admin = Admin(args.url, password)
    harden(admin)
    if args.admin_mfa == "on":
        admin_mfa_on(admin)
    elif args.admin_mfa == "off":
        admin_mfa_off(admin)


if __name__ == "__main__":
    main()
