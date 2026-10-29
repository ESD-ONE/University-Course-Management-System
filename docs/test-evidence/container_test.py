"""UCMS edge/container test — runs against the containerised deployment
(infra/docker-compose.deploy.yml): the web container's nginx on :8091, gateway replicas,
base-service replicas. Checks what only exists there: security headers, the Keycloak
admin lockdown, load balancing across replicas, failover, rate limits at both layers,
network isolation and container hardening. Results -> container_test_results.json.

    UCMS_PROJECT=ucms-app-test python3 container_test.py
"""
import base64
import hashlib
import json
import os
import re
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

from keycloak_login import REDIRECT_URI, login

EDGE = os.environ.get("UCMS_EDGE", "http://localhost:8091")
PROJECT = os.environ.get("UCMS_PROJECT", "ucms-app-test")
ADMIN = (os.environ.get("UCMS_ADMIN", "admin@ucms.local"), os.environ.get("UCMS_ADMIN_PASSWORD", "ChangeMe123!"))
results = []


def http(method, path, token=None, data=None, headers=None, timeout=30):
    h = dict(headers or {})
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(EDGE + path, data=data, method=method, headers=h)
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, dict(r.headers.items()), r.read(), time.time() - started
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers.items()), e.read(), time.time() - started


def docker(*args):
    p = subprocess.run(["docker", *args], capture_output=True, text=True)
    return p.returncode, (p.stdout + p.stderr).strip()


def containers(service):
    _, out = docker("ps", "-q", "--filter", f"label=com.docker.compose.project={PROJECT}",
                    "--filter", f"label=com.docker.compose.service={service}")
    return out.split()


def step(sid, desc, expected, ok, actual):
    results.append({"id": sid, "description": desc, "expected": expected, "actual": actual, "pass": bool(ok)})
    print(f"{'PASS' if ok else 'FAIL'}  {sid}  {desc}  ->  {actual}")


# ---------------------------------------------------------------- edge headers
s, h, body, _ = http("GET", "/courses")  # a client-side route: served as index.html
csp = h.get("Content-Security-Policy", "")
need = ["Strict-Transport-Security", "X-Content-Type-Options", "Referrer-Policy", "Permissions-Policy",
        "Cross-Origin-Opener-Policy"]
step("C-01", "App pages carry the security headers and a strict CSP; nginx hides its version",
     "all headers, script-src 'self', Server: nginx",
     s == 200 and all(n in h for n in need) and "script-src 'self'" in csp and "unsafe-eval" not in csp
     and h.get("Server") == "nginx",
     {"missing": [n for n in need if n not in h], "csp": csp[:80] + "...", "server": h.get("Server")})

s, h, body, _ = http("GET", "/silent-check-sso.html")
step("C-02", "Silent-SSO page works under the CSP: external script, no inline code", "200, <script src=...>",
     s == 200 and b'src="/silent-check-sso.js"' in body and b"postMessage" not in body,
     {"status": s, "csp": "Content-Security-Policy" in h})

s, h, body, _ = http("GET", "/index.html")
step("C-03", "Production build has no inline event handlers (would be blocked by the CSP)", "no onload=",
     s == 200 and b"onload=" not in body, {"status": s, "inlineHandlers": len(re.findall(rb"\son\w+=", body))})

# ---------------------------------------------------------------- Keycloak lockdown
blocked = {p: http("GET", p)[0] for p in
           ["/auth/admin/", "/auth/admin/master/console/", "/auth/realms/master/protocol/openid-connect/certs",
            "/auth/health/ready", "/auth/metrics"]}
s_wk, _, _, _ = http("GET", "/auth/realms/ucms/.well-known/openid-configuration")
step("C-04", "Keycloak admin console, master realm, health and metrics are not reachable from the site",
     "all 404; ucms realm 200", all(v == 404 for v in blocked.values()) and s_wk == 200, {**blocked, "ucms": s_wk})

verifier = "v" * 50
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
login_page = EDGE + "/auth/realms/ucms/protocol/openid-connect/auth?" + urllib.parse.urlencode(
    {"client_id": "ucms-web", "response_type": "code", "redirect_uri": REDIRECT_URI, "scope": "openid",
     "code_challenge": challenge, "code_challenge_method": "S256"})
with urllib.request.urlopen(login_page) as r:
    kc_status, kc = r.status, r.headers
kc_csp = kc.get_all("Content-Security-Policy") or []
dupes = [n for n in ("Referrer-Policy", "Strict-Transport-Security", "X-Content-Type-Options")
         if len(kc.get_all(n) or []) > 1]
step("C-05", "Keycloak's sign-in page keeps its own CSP and headers — the app's aren't stacked on top",
     "1 Keycloak CSP, no duplicated headers, + Permissions-Policy",
     kc_status == 200 and len(kc_csp) == 1 and "fonts.googleapis" not in kc_csp[0] and not dupes
     and kc.get("Permissions-Policy") and kc.get("Referrer-Policy") == "no-referrer",
     {"status": kc_status, "csp": kc_csp, "duplicated": dupes, "referrer": kc.get_all("Referrer-Policy")})

# ---------------------------------------------------------------- load balancing
token = login(*ADMIN)["access_token"]
bases = containers("base-service")
# Both replicas must be up (a previous run's failover test restarts one) and the gateways'
# health checks must have seen them, before measuring the spread.
deadline = time.time() + 240
while time.time() < deadline and any(
        docker("inspect", "-f", "{{.State.Health.Status}}", c)[1] != "healthy" for c in bases):
    time.sleep(5)
time.sleep(20)
gateways = containers("gateway")
before = {c: docker("exec", c, "sh", "-c", "cat /tmp/access/*.log 2>/dev/null | grep -c 'GET /departments'")[1] for c in bases}
codes = [http("GET", "/api/base/departments", token)[0] for _ in range(24)]
time.sleep(1)
after = {c: docker("exec", c, "sh", "-c", "cat /tmp/access/*.log 2>/dev/null | grep -c 'GET /departments'")[1] for c in bases}
served = {c[:12]: int(after[c] or 0) - int(before[c] or 0) for c in bases}
_, edge_log = docker("logs", "--since", "2m", "ucms-web")
upstreams = sorted(set(re.findall(r'GET /api/base/departments[^"]*" 200 .* upstream=(\S+)', edge_log)))
step("C-06", "Reads are spread over both base-service replicas, through both gateway replicas",
     "24 x 200; each base replica > 0; 2 gateway upstreams",
     codes.count(200) == 24 and len(bases) == 2 and all(v > 0 for v in served.values()) and len(upstreams) >= 2,
     {"codes": sorted(set(codes)), "perBaseReplica": served, "gatewayUpstreams": upstreams})

# ---------------------------------------------------------------- failover
stop_at, failures, done = 6, [], []
def load():
    for i in range(40):
        s, _, _, took = http("GET", "/api/base/departments", token, timeout=20)
        done.append((i, s, round(took, 2)))
        if s != 200:
            failures.append((i, s))
        time.sleep(0.15)
t = threading.Thread(target=load)
t.start()
while len(done) < stop_at:
    time.sleep(0.05)
victim = bases[0]
docker("kill", victim)  # abrupt: no graceful shutdown, the worst case
t.join()
slowest = max(took for _, _, took in done)
step("C-07", "Killing a base-service replica mid-traffic: requests keep succeeding on the other one",
     "40 x 200 (retried on the healthy replica)", not failures and len(done) == 40,
     {"failures": failures, "requests": len(done), "slowest_s": slowest, "killed": victim[:12]})
docker("start", victim)

# ---------------------------------------------------------------- circuit breaker / fallback
s, h, body, took = http("GET", "/api/enrollment/catalog", token)
step("C-08", "A service with no running replica answers with the gateway's 503 fallback, quickly",
     "503 SERVICE_UNAVAILABLE in < 5 s", s == 503 and b"SERVICE_UNAVAILABLE" in body and took < 5,
     {"status": s, "seconds": round(took, 2), "body": body[:90].decode(errors="replace")})

# ---------------------------------------------------------------- rate limits
time.sleep(3)
fresh = login(*ADMIN)["access_token"]
# The user-writes tier (2/s, burst 10): small enough to exhaust even on slow hardware.
with ThreadPoolExecutor(max_workers=12) as pool:
    burst = list(pool.map(lambda _: http("POST", "/api/identity/users", fresh, b"{}",
                                         {"Content-Type": "application/json"}), range(30)))
limited = next((r for r in burst if r[0] == 429), None)
passed_through = [r for r in burst if r[0] != 429]
checked = all("X-RateLimit-Remaining" in r[1] for r in passed_through)
step("C-09", "Gateway per-user limit, one bucket shared by both gateway replicas through Redis",
     "~10-16 through (each with X-RateLimit-Remaining), the rest 429 RATE_LIMITED + Retry-After",
     limited is not None and b"RATE_LIMITED" in limited[2] and "Retry-After" in limited[1]
     and 10 <= len(passed_through) <= 18 and checked,
     {"through": len(passed_through), "limited": sum(1 for r in burst if r[0] == 429),
      "allCheckedInRedis": checked, "retryAfter": limited[1].get("Retry-After") if limited else None})

floods = [http("POST", "/auth/realms/ucms/login-actions/authenticate", data=b"username=x&password=y",
               headers={"Content-Type": "application/x-www-form-urlencoded"})[0] for _ in range(45)]
step("C-10", "nginx flood guard on Keycloak's password form (per IP, burst 30)", "429s after ~30",
     floods.count(429) >= 10 and floods[0] != 429, {"first": floods[0], "429s": floods.count(429)})

# ---------------------------------------------------------------- request size
big = b"x" * (2 * 1024 * 1024)
s_json, _, _, _ = http("POST", "/api/base/departments", fresh, big, {"Content-Type": "application/json"})
s_upload, _, _, _ = http("POST", "/api/grading/assignments/00000000-0000-0000-0000-000000000000/submissions",
                         fresh, big, {"Content-Type": "application/octet-stream"})
step("C-11", "2 MB body: refused by nginx on normal routes, accepted through to the upload route",
     "413 / not 413", s_json == 413 and s_upload != 413, {"json": s_json, "upload": s_upload})

# ---------------------------------------------------------------- isolation & hardening
web_pg = docker("exec", "ucms-web", "getent", "hosts", "ucms-postgres")[0]
web_svc = docker("exec", "ucms-web", "getent", "hosts", "base-service")[0]
gw_pg = docker("exec", gateways[0], "getent", "hosts", "ucms-postgres")[0]
base_pg = docker("exec", bases[1], "getent", "hosts", "ucms-postgres")[0]
step("C-12", "Network segmentation: web reaches neither DB nor services; gateway can't see the DB; services can",
     "web/gateway: unresolvable; base: resolves",
     web_pg != 0 and web_svc != 0 and gw_pg != 0 and base_pg == 0,
     {"web->postgres": web_pg, "web->base": web_svc, "gateway->postgres": gw_pg, "base->postgres": base_pg})

uid = docker("exec", bases[1], "id", "-u")[1]
ro = docker("exec", bases[1], "touch", "/app/x")
caps = docker("inspect", "-f", "{{.HostConfig.CapDrop}} {{.HostConfig.ReadonlyRootfs}} {{.HostConfig.SecurityOpt}}",
              bases[1])[1]
step("C-13", "Service containers run as a non-root user on a read-only filesystem with no capabilities",
     "uid != 0, write refused, CapDrop [ALL]",
     uid not in ("0", "") and ro[0] != 0 and "Read-only" in ro[1] and "[ALL] true" in caps,
     {"uid": uid, "write": ro[1][-40:], "inspect": caps})

passed = sum(r["pass"] for r in results)
print(f"\n{passed}/{len(results)} passed")
with open(__file__.replace("container_test.py", "container_test_results.json"), "w") as f:
    json.dump({"at": datetime.now().isoformat(), "passed": passed, "total": len(results), "results": results}, f, indent=2)
