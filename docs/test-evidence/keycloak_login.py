"""Signs in the way the browser does: Authorization Code + PKCE through Keycloak's own
login page, as the ucms-web client. No password grant, no test-only client — the realm
is exercised exactly as configured.

    from keycloak_login import login
    tokens = login("admin@ucms.local", "ChangeMe123!")   # -> dict with access_token, ...

Standard library only.
"""
import base64
import hashlib
import html
import http.cookiejar
import json
import os
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK = os.environ.get("KEYCLOAK_URL", "http://localhost:8180/auth")
REALM = "ucms"
CLIENT_ID = "ucms-web"
REDIRECT_URI = os.environ.get("APP_URL", "http://localhost:4200") + "/app"


class LoginFailed(Exception):
    """Keycloak showed its login page again — wrong password, disabled account, ..."""

    def __init__(self, message):
        super().__init__(message)
        self.message = message


class _LocalhostIsSecure(http.cookiejar.DefaultCookiePolicy):
    """Keycloak marks its cookies Secure. Browsers still send them to http://localhost
    (a "potentially trustworthy" origin); Python's cookie jar doesn't, so match browsers."""

    def return_ok_secure(self, cookie, request):
        host = urllib.parse.urlparse(request.get_full_url()).hostname
        return host in ("localhost", "127.0.0.1") or super().return_ok_secure(cookie, request)


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def login(username, password):
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    jar = http.cookiejar.CookieJar(policy=_LocalhostIsSecure())
    browser = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), _NoRedirect)

    auth_url = f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/auth?" + urllib.parse.urlencode({
        "client_id": CLIENT_ID, "redirect_uri": REDIRECT_URI, "response_type": "code",
        "scope": "openid", "state": secrets.token_hex(8),
        "code_challenge": challenge, "code_challenge_method": "S256",
    })
    page = browser.open(auth_url).read().decode()
    action = html.unescape(re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', page).group(1))

    form = urllib.parse.urlencode({"username": username, "password": password, "credentialId": ""}).encode()
    try:
        response = browser.open(action, form)
        # 200 = the login page was shown again, with an error message.
        body = response.read().decode()
        error = re.search(r'id="input-error"[^>]*>\s*([^<]+)', body) or re.search(
            r'class="[^"]*(?:alert-error|kc-feedback-text)[^"]*"[^>]*>\s*(?:<[^>]+>\s*)*([^<]+)', body)
        raise LoginFailed(html.unescape(error.group(1).strip()) if error else "login page shown again")
    except urllib.error.HTTPError as redirect:
        if redirect.code not in (302, 303):
            raise
        location = redirect.headers["Location"]

    query = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)
    if "code" not in query:
        # Not back at the app: Keycloak wants another step first (e.g. setting up an
        # authenticator app when administrators must use one). Report which.
        try:
            step = browser.open(location).read().decode()
        except urllib.error.HTTPError as e:
            step = e.read().decode(errors="replace")
        title = re.search(r'id="kc-page-title"[^>]*>\s*([^<]+)', step) or re.search(r"<title>([^<]+)", step)
        raise LoginFailed("further step required: " + (html.unescape(title.group(1).strip()) if title else location))
    code = query["code"][0]
    token_request = urllib.request.Request(
        f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token",
        data=urllib.parse.urlencode({
            "grant_type": "authorization_code", "client_id": CLIENT_ID, "code": code,
            "redirect_uri": REDIRECT_URI, "code_verifier": verifier,
        }).encode(),
    )
    return json.load(urllib.request.urlopen(token_request))


def claims(token):
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


if __name__ == "__main__":
    import sys
    t = login(sys.argv[1], sys.argv[2])
    c = claims(t["access_token"])
    print(json.dumps({k: c.get(k) for k in ("iss", "azp", "uid", "realm_access", "exp", "iat")}, indent=2))
