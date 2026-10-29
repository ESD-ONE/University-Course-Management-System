"""UCMS end-to-end system test — drives the running stack through the Gateway (:8082),
exactly as the Angular app does, signing in through Keycloak's login page (see
keycloak_login.py). Each step records expected vs actual; a step passes only if the
observed response matches. Results -> system_test_results.json."""
import json
import pathlib
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone

from keycloak_login import KEYCLOAK, LoginFailed, claims, login as keycloak_login

GW = "http://localhost:8082"
RABBIT = "http://127.0.0.1:15673"
MAILPIT = "http://127.0.0.1:8025"
RUN = uuid.uuid4().hex[:6]
results = []
# Headers of the most recent response, and how often the gateway's rate limit made a call
# wait (a well-behaved client honours Retry-After and tries again, as this one does).
LAST_HEADERS = {}
THROTTLED = []


def call(method, path, token=None, body=None, raw=None, headers=None, base=GW, auth=None, retry429=True):
    for attempt in range(6):
        s, b = _call(method, path, token, body, raw, headers, base, auth)
        if s != 429 or not retry429:
            return s, b
        wait = min(int(LAST_HEADERS.get("Retry-After") or 1), 10)
        THROTTLED.append({"path": path, "wait": wait})
        time.sleep(wait)
    return s, b


def _call(method, path, token, body, raw, headers, base, auth):
    h = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    if raw is not None:
        data = raw
    if token:
        h["Authorization"] = "Bearer " + str(token)
    req = urllib.request.Request(base + path, data=data, method=method, headers=h)
    if auth:
        import base64
        req.add_header("Authorization", "Basic " + base64.b64encode(auth.encode()).decode())
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            LAST_HEADERS.clear()
            LAST_HEADERS.update(r.headers.items())
            content = r.read()
            ctype = r.headers.get("Content-Type", "")
            return r.status, (json.loads(content) if "json" in ctype and content else content)
    except urllib.error.HTTPError as e:
        LAST_HEADERS.clear()
        LAST_HEADERS.update(e.headers.items())
        content = e.read()
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, content


def step(sid, desc, expected, ok, actual):
    results.append({"id": sid, "description": desc, "expected": expected, "actual": actual, "pass": bool(ok)})
    print(f"{'PASS' if ok else 'FAIL'}  {sid}  {desc}  ->  {actual}")


def multipart(field, filename, content, ctype="application/zip"):
    boundary = "----ucms" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {ctype}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}


class Session:
    """A signed-in user. Access tokens last five minutes, so — like keycloak-js in the
    browser — str(session) refreshes the token (refresh_token grant) when it's within 30s
    of expiring. Pass it wherever a token is expected."""

    def __init__(self, tokens):
        self._set(tokens)

    def _set(self, tokens):
        self.access, self.refresh = tokens["access_token"], tokens["refresh_token"]
        self.expires = claims(self.access)["exp"]

    def __str__(self):
        if time.time() > self.expires - 30:
            data = urllib.parse.urlencode({"grant_type": "refresh_token", "client_id": "ucms-web",
                                           "refresh_token": self.refresh}).encode()
            with urllib.request.urlopen(f"{KEYCLOAK}/realms/ucms/protocol/openid-connect/token", data) as r:
                self._set(json.load(r))
        return self.access


def login(email, password):
    """A Session from Keycloak's real login flow, or None if Keycloak refused."""
    try:
        return Session(keycloak_login(email, password))
    except LoginFailed:
        return None


def login_error(email, password):
    try:
        keycloak_login(email, password)
        return None
    except LoginFailed as e:
        return e.message


def env_file():
    path = pathlib.Path(__file__).resolve().parents[2] / "infra" / ".env"
    pairs = (l.split("=", 1) for l in path.read_text().splitlines() if "=" in l and not l.startswith("#"))
    return {k.strip(): v.strip() for k, v in pairs}


def service_token(client_id, secret):
    data = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": client_id,
                                   "client_secret": secret}).encode()
    with urllib.request.urlopen(f"{KEYCLOAK}/realms/ucms/protocol/openid-connect/token", data) as r:
        return json.load(r)["access_token"]


def wait_for(fn, timeout=20, interval=1.0):
    end = time.time() + timeout
    while time.time() < end:
        v = fn()
        if v:
            return v
        time.sleep(interval)
    return None


def notifications(token):
    s, b = call("GET", "/api/notification/notifications/me?size=50", token)
    return b.get("items", []) if s == 200 else []


# ---------------------------------------------------------------- identity
admin = login("admin@ucms.local", "ChangeMe123!")
step("ST-01", "Migrated admin signs in at Keycloak with their pre-Keycloak password (BCrypt import)",
     "Authorization Code + PKCE succeeds", admin is not None, "token issued" if admin else "login failed")
if admin:
    c = claims(str(admin))
    s, me = call("GET", "/api/identity/users/me", admin)
    step("ST-01b", "Keycloak token identifies the UCMS account by its uid claim", "uid == /users/me id, iss = realm",
         s == 200 and me.get("id") == c.get("uid") and c.get("iss", "").endswith("/realms/ucms"),
         {"uid": c.get("uid"), "me": me.get("id") if isinstance(me, dict) else s, "iss": c.get("iss")})

s, b = call("GET", "/api/enrollment/catalog")
step("ST-02", "Gateway rejects a request with no token", "401", s == 401, s)

people = {}
for key, name, role in [("L1", "Dr. N. Silva", "LECTURER"), ("L2", "Dr. A. Bandara", "LECTURER"),
                        ("S1", "K. Perera", "STUDENT"), ("S2", "A. Jayawardena", "STUDENT"),
                        ("S3", "C. Ranasinghe", "STUDENT")]:
    email = f"{key.lower()}.{RUN}@uni.edu"
    s, b = call("POST", "/api/identity/users", admin, {"name": name, "email": email, "role": role})
    people[key] = {"id": b.get("id") if isinstance(b, dict) else None, "email": email,
                   "password": b.get("temporaryPassword") if isinstance(b, dict) else None,
                   "studentId": b.get("studentId") if isinstance(b, dict) else None}
ok = all(p["id"] and p["password"] for p in people.values())
step("ST-03", "Admin creates 2 lecturers + 3 students (temp passwords issued once)", "5 x 201", ok,
     {k: v["studentId"] or "staff" for k, v in people.items()})
tok = {k: login(v["email"], v["password"]) for k, v in people.items()}
step("ST-03b", "Each new account was created in Keycloak and signs in with its temporary password", "5 tokens",
     all(tok.values()), {k: bool(v) for k, v in tok.items()})

welcome = wait_for(lambda: [n for n in notifications(tok["S1"]) if n["type"] == "identity.user-created"])
step("ST-04", "identity.user-created → Notification creates a welcome notification", "1 SYSTEM notification",
     bool(welcome), welcome[0]["title"] if welcome else "none after 20s")

# ---------------------------------------------------------------- academic setup
s, years = call("GET", "/api/academic/academic-years", admin)
year = next(y for y in years if y["status"] == "ACTIVE")
semester = (year.get("semesters") or [None])[0]
s, programs = call("GET", "/api/academic/degree-programs", admin)
program = programs[0]
s, catalog = call("GET", "/api/academic/course-catalog", admin)
course_body = {"catalogCourseId": catalog[0]["id"], "degreeProgramId": program["id"], "beginDate": str(date.today()),
               "yearOfStudy": 1, "capacity": 2, "status": "PUBLISHED", "lecturerId": people["L1"]["id"]}
s, course = call("POST", "/api/academic/courses", admin, course_body)
step("ST-05", "Admin creates a course template taught by L1 (capacity 2)", "201", s == 201,
     f"{s} {course.get('catalogCourseCode') if isinstance(course, dict) else course}")
s, offering = call("POST", f"/api/academic/courses/{course['id']}/academic-years", admin,
                   {"academicYearId": year["id"], "capacity": 2, "status": "PUBLISHED"})
off_id = offering["id"] if isinstance(offering, dict) else None
step("ST-06", f"Course offered in active year {year['name']} (no semester window)", "201 PUBLISHED", s == 201, s)
s, off = call("GET", f"/api/academic/course-offerings/{off_id}", tok["S1"])
step("ST-07", "New course-offerings endpoint resolves capacity/lecturer/open state", "open, cap 2, lecturer L1",
     s == 200 and off["openForEnrollment"] and off["capacity"] == 2 and off["lecturerId"] == people["L1"]["id"],
     {k: off.get(k) for k in ("code", "capacity", "openForEnrollment")} if s == 200 else s)

# A second course placed in a semester whose enrollment window has already closed.
if semester and semester.get("enrollmentCloseAt"):
    s, course2 = call("POST", "/api/academic/courses", admin, {**course_body, "catalogCourseId": catalog[1]["id"]})
    s, closed = call("POST", f"/api/academic/courses/{course2['id']}/academic-years", admin,
                     {"academicYearId": year["id"], "semesterId": semester["id"], "capacity": 5, "status": "PUBLISHED"})
    s_off, closed_off = call("GET", f"/api/academic/course-offerings/{closed['id']}", tok["S1"])
    s_enr, b = call("POST", "/api/enrollment/course-enrollments", tok["S1"], {"offeringId": closed["id"]})
    step("ST-07b", f"Semester window closed ({semester['enrollmentCloseAt'][:10]}) → students blocked", "open=false, 409 NOT_OPEN",
         closed_off.get("openForEnrollment") is False and s_enr == 409 and b.get("code") == "NOT_OPEN",
         [closed_off.get("openForEnrollment"), s_enr, b.get("code") if isinstance(b, dict) else b])

# ---------------------------------------------------------------- enrollment (flow-03 + flow-01)
reg = {"studentId": people["S1"]["id"], "degreeProgramId": program["id"], "intakeAcademicYearId": year["id"],
       "currentYearOfStudy": 1}
s, b = call("POST", "/api/enrollment/program-enrollments", admin, reg)
step("ST-08", "flow-03: admin registers S1 into a degree program", "201 ACTIVE", s == 201 and b.get("status") == "ACTIVE",
     f"{s} {b.get('degreeProgramName') if isinstance(b, dict) else b}")
s, b = call("POST", "/api/enrollment/program-enrollments", admin, reg)
step("ST-09", "Second active program registration for the same student is rejected", "409 ALREADY_REGISTERED",
     s == 409 and b.get("code") == "ALREADY_REGISTERED", f"{s} {b.get('code') if isinstance(b, dict) else ''}")
s, b = call("POST", "/api/enrollment/program-enrollments", tok["L1"], reg)
step("ST-09b", "Lecturer cannot register students into programs", "403", s == 403, s)

s, cat = call("GET", "/api/enrollment/catalog", tok["S1"])
entry = next((c for c in cat if c["offeringId"] == off_id), None) if s == 200 else None
step("ST-10", "S1 browses the catalog and sees live seats", "seatsLeft 2", entry and entry["seatsLeft"] == 2,
     {k: entry[k] for k in ("code", "seatsLeft", "lecturerName")} if entry else s)

s1 = call("POST", "/api/enrollment/course-enrollments", tok["S1"], {"offeringId": off_id})
s2 = call("POST", "/api/enrollment/course-enrollments", tok["S2"], {"offeringId": off_id})
step("ST-11", "flow-01: S1 and S2 self-enroll", "201 ENROLLED x2",
     s1[0] == 201 and s2[0] == 201 and s1[1]["status"] == s2[1]["status"] == "ENROLLED", [s1[0], s2[0]])
s, b = call("POST", "/api/enrollment/course-enrollments", tok["S3"], {"offeringId": off_id})
step("ST-12", "flow-01 'course full' branch for S3", "409 COURSE_FULL", s == 409 and b.get("code") == "COURSE_FULL",
     f"{s} {b.get('message') if isinstance(b, dict) else ''}")
s, b = call("POST", "/api/enrollment/course-enrollments", tok["S3"], {"offeringId": off_id, "joinWaitlistIfFull": True})
step("ST-13", "S3 joins the waitlist", "201 WAITLISTED", s == 201 and b.get("status") == "WAITLISTED", s)
s, b = call("POST", "/api/enrollment/course-enrollments", tok["S1"], {"offeringId": off_id})
step("ST-13b", "Duplicate enrollment rejected", "409 ALREADY_ENROLLED", s == 409 and b.get("code") == "ALREADY_ENROLLED", s)

s, _ = call("GET", f"/api/enrollment/course-enrollments?offeringId={off_id}", tok["L2"])
s_ok, roster = call("GET", f"/api/enrollment/course-enrollments?offeringId={off_id}", tok["L1"])
step("ST-14", "Roster: other lecturer 403, teaching lecturer sees 3 rows with names/IDs", "403 / 200 x3",
     s == 403 and s_ok == 200 and len(roster) == 3 and all(r["studentNumber"] for r in roster),
     f"L2={s} L1={s_ok} rows={len(roster) if isinstance(roster, list) else roster}")

s1_enrollment = s1[1]["id"]
s, _ = call("DELETE", f"/api/enrollment/course-enrollments/{s1_enrollment}", tok["S1"])
s, mine = call("GET", "/api/enrollment/course-enrollments/me", tok["S3"])
s3_status = next((e["status"] for e in mine if e["offeringId"] == off_id), None)
step("ST-15", "S1 drops → S3 promoted from the waitlist automatically", "S3 ENROLLED", s3_status == "ENROLLED", s3_status)
promo = wait_for(lambda: [n for n in notifications(tok["S3"]) if n["type"] == "enrollment.waitlist-promoted"])
step("ST-16", "S3 notified of the promotion (event → Notification)", "notification present", bool(promo),
     promo[0]["title"] if promo else "none")

s, regs = call("GET", "/api/enrollment/registrations?limit=10", admin)
actions = [r["action"] for r in regs] if s == 200 else []
step("ST-17", "Registration log records every action with actor", "PROGRAM_REGISTER, COURSE_ENROLL, COURSE_WAITLIST, COURSE_DROP, WAITLIST_PROMOTED",
     {"PROGRAM_REGISTER", "COURSE_ENROLL", "COURSE_WAITLIST", "COURSE_DROP", "WAITLIST_PROMOTED"} <= set(actions), actions)

# ---------------------------------------------------------------- assignment & grading (flow-02)
due = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()
a_body = {"offeringId": off_id, "title": f"Programming Exercise {RUN}", "description": "CLI calculator", "dueDate": due, "maxMarks": 100}
s, assignment = call("POST", "/api/grading/assignments", tok["L1"], a_body)
a_id = assignment.get("id") if isinstance(assignment, dict) else None
step("ST-18", "Teaching lecturer creates an assignment", "201 DRAFT", s == 201 and assignment.get("status") == "DRAFT", s)
s, _ = call("POST", "/api/grading/assignments", tok["L2"], a_body)
step("ST-19", "A different lecturer cannot create one on L1's course", "403", s == 403, s)
s, lst = call("GET", "/api/grading/assignments", tok["S2"])
step("ST-20", "Students can't see a draft", "0 visible", s == 200 and not any(x["id"] == a_id for x in lst), len(lst) if s == 200 else s)
s, _ = call("PATCH", f"/api/grading/assignments/{a_id}/status", tok["L1"], {"status": "OPEN"})
published = wait_for(lambda: [n for n in notifications(tok["S2"]) if n["type"] == "grading.assignment-published"])
step("ST-21", "Publish → grading.assignment-published fans out to enrolled students", "S2 notified",
     s == 200 and bool(published), published[0]["title"] if published else f"status {s}, no notification")

payload = b"PK\x03\x04 ucms submission " + RUN.encode()
body, hdr = multipart("file", "ex2_calc.zip", payload)
s, sub = call("POST", f"/api/grading/assignments/{a_id}/submissions", tok["S2"], raw=body, headers=hdr)
sub_id = sub.get("submissionId") if isinstance(sub, dict) else None
step("ST-22", "S2 uploads a submission (stored in MinIO)", "201 SUBMITTED", s == 201 and sub.get("status") == "SUBMITTED", s)
body, hdr = multipart("file", "x.zip", b"x")
s, _ = call("POST", f"/api/grading/assignments/{a_id}/submissions", tok["S1"], raw=body, headers=hdr)
step("ST-23", "S1 (dropped the course) cannot submit", "403", s == 403, s)
s, content = call("GET", f"/api/grading/submissions/{sub_id}/file", tok["L1"])
step("ST-24", "Lecturer downloads the submission — identical bytes back from MinIO", "same bytes",
     s == 200 and content == payload, f"{s} {len(content) if isinstance(content, bytes) else content} bytes")
s, rows = call("GET", f"/api/grading/assignments/{a_id}/submissions", tok["L1"])
statuses = {r["studentName"]: r["status"] for r in rows} if s == 200 else rows
step("ST-25", "Grading screen lists every enrolled student incl. non-submitters", "S2 SUBMITTED, S3 NOT_SUBMITTED",
     s == 200 and statuses.get("A. Jayawardena") == "SUBMITTED" and statuses.get("C. Ranasinghe") == "NOT_SUBMITTED", statuses)

s_bad, _ = call("PUT", f"/api/grading/submissions/{sub_id}/grade", tok["L1"], {"marks": 120})
s, g = call("PUT", f"/api/grading/submissions/{sub_id}/grade", tok["L1"], {"marks": 88, "feedback": "Good work"})
step("ST-26", "flow-02: marks above max rejected; valid grade saved", "400 then 200", s_bad == 400 and s == 200,
     [s_bad, s])
graded = wait_for(lambda: [n for n in notifications(tok["S2"]) if n["type"] == "grading.grade-posted"])
step("ST-27", "grading.grade-posted → student notified", "notification present", bool(graded), graded[0]["body"] if graded else "none")
s, hist = call("GET", "/api/grading/grades/me", tok["S2"])
course_row = hist["courses"][0] if s == 200 and hist["courses"] else {}
step("ST-28", "Student grade history: percentage, letter (from base-service scale), GPA", "88.0% A, GPA 4.0",
     course_row.get("percentage") == 88.0 and course_row.get("letter") == "A" and hist.get("gpa") == 4.0,
     {"pct": course_row.get("percentage"), "letter": course_row.get("letter"), "gpa": hist.get("gpa") if s == 200 else s})
body, hdr = multipart("file", "again.zip", b"again")
s, b = call("POST", f"/api/grading/assignments/{a_id}/submissions", tok["S2"], raw=body, headers=hdr)
step("ST-29", "Resubmitting after grading is blocked", "409 ALREADY_GRADED", s == 409 and b.get("code") == "ALREADY_GRADED", s)

# ---------------------------------------------------------------- analytics & reporting
s, d = call("POST", "/api/analytics/dashboards/admin/refresh", admin)
top_list = (d.get("topCourses") or []) if s == 200 else []
top = next((t for t in top_list if t["offeringId"] == off_id), None)
# The list is the top 5 by enrollment; on a database holding earlier runs, this run's course
# (2 enrolled) can be tied out of it by older 2-enrolled courses — that's still correct.
tied_out = top is None and len(top_list) == 5 and all(t["enrolled"] >= 2 for t in top_list)
step("ST-30", "Admin dashboard recomputed from IAM/ACAD/ENR/GRADE", "students >= 3, our course enrolled 2 (or tied out of the top 5)",
     s == 200 and d["kpis"]["totalStudents"] >= 3 and ((top and top["enrolled"] == 2) or tied_out),
     {"kpis": d.get("kpis") if s == 200 else s, "top": top or ("tied out of top 5" if tied_out else None)})
s, d = call("GET", "/api/analytics/dashboards/lecturer", tok["L1"])
c = next((x for x in d.get("courses", []) if x["offeringId"] == off_id), None) if s == 200 else None
step("ST-31", "Lecturer dashboard: class average from grading", "classAverage 88.0", c and c["classAverage"] == 88.0,
     {k: c[k] for k in ("enrolled", "graded", "classAverage")} if c else s)
s, d = call("GET", "/api/analytics/dashboards/student", tok["S2"])
step("ST-32", "Student dashboard: GPA and current grade", "gpa 4.0, 1 course", s == 200 and d["gpa"] == 4.0 and d["enrolledCourses"] == 1,
     {"gpa": d.get("gpa"), "courses": d.get("enrolledCourses"), "deadlines": d.get("upcomingDeadlines")} if s == 200 else s)
s, d = call("GET", "/api/analytics/dashboards/student", tok["S1"])
step("ST-32b", "Student dashboard shows the program registration", "BSc program shown",
     s == 200 and d["programName"] == program["name"], d.get("programName") if s == 200 else s)

s, reports = call("GET", "/api/analytics/reports", admin)
by = {r["name"]: r for r in reports} if s == 200 else {}
step("ST-33", "Report library seeded (6 reports)", "6", len(by) == 6, list(by))
rid = by["Course Pass Rate"]["id"]
never_generated = not by["Course Pass Rate"]["available"]
s, _ = call("GET", f"/api/analytics/reports/{rid}/download", admin)
s_gen, r = call("POST", f"/api/analytics/reports/{rid}/generate", admin)
s_dl, csv = call("GET", f"/api/analytics/reports/{rid}/download", admin)
csv_text = csv.decode("utf-8-sig") if isinstance(csv, bytes) else str(csv)
step("ST-34", "CSV report: 404 before first generate; generate → MinIO; download has this run's course",
     "404 (if never generated), 200, CSV w/ course code",
     (s == 404 or not never_generated) and s_gen == 200 and off["code"] in csv_text and str(off_id) not in csv_text,
     [s, s_gen, s_dl, "first generate" if never_generated else "regenerated"])
pid = by["Grade Distribution"]["id"]
call("POST", f"/api/analytics/reports/{pid}/generate", admin)
s, pdf = call("GET", f"/api/analytics/reports/{pid}/download", admin)
step("ST-35", "PDF report is a real PDF", "%PDF-", isinstance(pdf, bytes) and pdf[:5] == b"%PDF-", pdf[:5] if isinstance(pdf, bytes) else s)
s, sch = call("POST", "/api/analytics/scheduled-reports", admin,
              {"reportId": rid, "frequency": "WEEKLY", "time": "08:00", "dayOfWeek": "MONDAY", "recipients": "r.fernando@university.edu"})
step("ST-36", "Schedule a weekly report", "201 + next run", s == 201 and sch.get("nextRunAt"),
     {"label": sch.get("frequencyLabel"), "next": sch.get("nextRunAt")} if s == 201 else s)
s, _ = call("PATCH", f"/api/analytics/scheduled-reports/{sch['id']}/status", admin, {"status": "PAUSED"})
step("ST-36b", "Pause the schedule", "200", s == 200, s)
s, _ = call("GET", "/api/analytics/reports", tok["L1"])
step("ST-37", "Reports are admin-only", "403", s == 403, s)

# ---------------------------------------------------------------- notifications: live push + preferences
received = []


def listen(token):
    req = urllib.request.Request(GW + "/api/notification/notifications/stream",
                                 headers={"Authorization": "Bearer " + str(token), "Accept": "text/event-stream"})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            for line in r:
                received.append(line.decode().strip())
                if line.startswith(b"event:notification"):
                    return
    except Exception as e:
        received.append(f"ERR {e}")


t = threading.Thread(target=listen, args=(tok["S2"],), daemon=True)
t.start()
time.sleep(2)
call("PUT", f"/api/grading/submissions/{sub_id}/grade", tok["L1"], {"marks": 90, "feedback": "Regraded"})
t.join(20)
step("ST-38", "Live push (SSE through the Gateway): regrade arrives on the open stream", "event:notification received",
     "event:notification" in received, [x for x in received if x.startswith("event")][:3])

s, u = call("GET", "/api/notification/notifications/me/unread-count", tok["S2"])
before = u.get("count") if s == 200 else None
call("POST", "/api/notification/notifications/me/read-all", tok["S2"])
s, u = call("GET", "/api/notification/notifications/me/unread-count", tok["S2"])
step("ST-39", "Unread count, then mark all read", ">0 then 0", before and before > 0 and u.get("count") == 0, [before, u.get("count")])

s, prefs = call("GET", "/api/notification/preferences/me", tok["S2"])
call("PUT", "/api/notification/preferences/me", tok["S2"], {**prefs, "gradeAlerts": False})
n_before = len([n for n in notifications(tok["S2"]) if n["type"] == "grading.grade-posted"])
call("PUT", f"/api/grading/submissions/{sub_id}/grade", tok["L1"], {"marks": 91})
time.sleep(6)
n_after = len([n for n in notifications(tok["S2"]) if n["type"] == "grading.grade-posted"])
step("ST-40", "Grade alerts turned off → no new grade notification", "count unchanged", n_after == n_before, [n_before, n_after])

s, mail = call("GET", "/api/v1/messages?limit=200", base=MAILPIT)
subjects = [m["Subject"] for m in mail.get("messages", [])] if s == 200 else []
step("ST-41", "Email channel delivered to SMTP (Mailpit)", "welcome + enrollment + grade emails",
     any("Welcome" in x for x in subjects) and any("Enrolled" in x for x in subjects) and any("Grade posted" in x for x in subjects),
     f"{len(subjects)} emails, e.g. {subjects[:3]}")

# ---------------------------------------------------------------- security
t_s3 = str(login(people["S3"]["email"], people["S3"]["password"]))
s_before, _ = call("GET", "/api/identity/users/me", t_s3)
call("POST", "/api/identity/auth/logout", t_s3)
s_after, b = call("GET", "/api/identity/users/me", t_s3)
step("ST-42", "G-11: logout revokes the token at the Gateway (Redis)", "200 then 401", s_before == 200 and s_after == 401,
     [s_before, s_after, b.get("message") if isinstance(b, dict) else ""])
s, _ = call("GET", "/api/identity/users/me", "not.a.real.token")
step("ST-43", "Malformed token rejected", "401", s == 401, s)
s, _ = call("GET", "/api/base/faculties", admin)
s_old, _ = call("GET", "/api/reference/faculties", admin)
step("ST-44", "Renamed route /api/base works; old /api/reference is gone", "200 / 404", s == 200 and s_old == 404, [s, s_old])

# ---------------------------------------------------------------- Keycloak
err = login_error(people["S1"]["email"], "definitely-not-the-password")
step("ST-50", "Keycloak rejects a wrong password on its own login page", "login page with an error",
     err is not None, err)

s, b = call("POST", "/api/identity/auth/login", body={"email": people["S1"]["email"], "password": people["S1"]["password"]})
step("ST-51", "The old password-to-token endpoint no longer exists", "4xx", 400 <= s < 500, s)

susp_email = f"s4.{RUN}@uni.edu"
s, b = call("POST", "/api/identity/users", admin, {"name": "D. Fernando", "email": susp_email, "role": "STUDENT"})
susp_pw, susp_id = b.get("temporaryPassword"), b.get("id")
before = login(susp_email, susp_pw)
call("PATCH", f"/api/identity/users/{susp_id}/status", admin, {"status": "SUSPENDED"})
err = login_error(susp_email, susp_pw)
step("ST-52", "Suspending an account on the Users screen disables it in Keycloak", "signs in, then refused",
     before is not None and err is not None, err)

t_s1 = login(people["S1"]["email"], people["S1"]["password"])
new_pw = "Changed-" + RUN + "!x"
s, _ = call("PATCH", "/api/identity/users/me/password", t_s1,
            {"currentPassword": people["S1"]["password"], "newPassword": new_pw})
s_wrong, _ = call("PATCH", "/api/identity/users/me/password", t_s1,
                  {"currentPassword": "not-my-password", "newPassword": "Whatever-123!"})
step("ST-53", "Profile password change is verified and applied by Keycloak", "200, new works, old refused, wrong current 400",
     s == 200 and login(people["S1"]["email"], new_pw) is not None
     and login(people["S1"]["email"], people["S1"]["password"]) is None and s_wrong == 400,
     {"change": s, "wrongCurrent": s_wrong})
people["S1"]["password"] = new_pw

secrets_ = env_file()
t_notif = service_token("svc-notification", secrets_["KC_SVC_NOTIFICATION_SECRET"])
s_allowed, _ = call("GET", f"/api/identity/users/{people['S2']['id']}", t_notif)
s_denied, _ = call("GET", "/api/academic/degree-programs", t_notif)
step("ST-54", "Service tokens are least-privilege: notification may call identity, not academic", "200 / 401-or-403",
     s_allowed == 200 and s_denied in (401, 403), [s_allowed, s_denied])

s, _ = call("GET", f"/api/identity/internal/assignments/{people['S2']['id']}", admin)
step("ST-55", "Internal assignments endpoint refuses people, even admins", "403", s == 403, s)

# ---------------------------------------------------------------- G-05 fix verification
s, depts = call("GET", "/api/base/departments", admin)
referenced = next(d for d in depts if d["id"] == program["departmentId"])
s, b = call("DELETE", f"/api/base/departments/{referenced['id']}", admin)
step("ST-45", "G-05: deleting a department still referenced by degree programs is blocked", "409",
     s == 409, f"{s} {b.get('message') if isinstance(b, dict) else ''}")

# ---------------------------------------------------------------- G-01 Department Head, end to end
other = next(d for d in depts if d["id"] != referenced["id"])
s, role = call("POST", "/api/identity/roles", admin, {"name": f"Department Head {RUN}", "kind": "LECTURER"})
s_perm, perms = call("GET", "/api/identity/permissions", admin)
for p in perms:
    if p["module"] in ("Degree Programs", "Courses") and p["actionKey"] in ("View", "Create", "Edit"):
        call("PATCH", f"/api/identity/roles/{role['id']}/permissions", admin, {"permissionId": p["id"], "granted": True})
s, asg = call("POST", f"/api/identity/users/{people['L2']['id']}/assignments", admin,
              {"roleId": role["id"], "scopeType": "DEPARTMENT", "scopeId": referenced["id"]})
step("ST-46", "Admin creates a Department Head role and scopes L2 to one department", "201", s == 201, s)
t_l2 = login(people["L2"]["email"], people["L2"]["password"])
s, me = call("GET", "/api/identity/users/me", t_l2)
step("ST-47", "G-01: /users/me now exposes the DEPARTMENT scope to the frontend", "DEPARTMENT assignment present",
     any(a["scopeType"] == "DEPARTMENT" for a in me.get("assignments", [])), [a["scopeType"] for a in me.get("assignments", [])])


def create_program(dept):
    # A fresh token per attempt, as the browser's silent refresh would: access tokens
    # last five minutes and this waits on a 60-second background sync.
    return call("POST", "/api/academic/degree-programs", login(people["L2"]["email"], people["L2"]["password"]),
                {"code": f"DH-{RUN}-{dept['code']}", "name": f"Dept Head test {RUN}", "departmentId": dept["id"],
                 "durationYears": 3, "totalCredits": 90})


# Role-permission grants reach academic-structure-service via its 60s catalog sync.
created = wait_for(lambda: create_program(referenced)[0] == 201, timeout=80, interval=5)
s_other, _ = create_program(other)
step("ST-48", "Department Head creates a program in their department; blocked elsewhere", "201 / 403",
     bool(created) and s_other == 403, f"own dept {'201' if created else 'not allowed within 80s'}, other dept {s_other}")

# ---------------------------------------------------------------- rate limits, request ids, password policy
t_s3 = login(people["S3"]["email"], people["S3"]["password"])
codes, limited = [], None
for _ in range(15):
    s, b = call("POST", "/api/enrollment/course-enrollments", t_s3, {}, retry429=False)
    codes.append(s)
    if s == 429 and limited is None:
        limited = {"retryAfter": LAST_HEADERS.get("Retry-After"), "body": b}
s_other, _ = call("POST", "/api/enrollment/course-enrollments", tok["S2"], {}, retry429=False)
s_read, _ = call("GET", "/api/enrollment/catalog", t_s3, retry429=False)
step("ST-56", "Per-user rate limit on enrollment writes: 429 + Retry-After; other users and reads unaffected",
     "first calls pass, then 429 RATE_LIMITED; S2 write and S3 read not limited",
     codes[0] != 429 and limited is not None and limited["retryAfter"] and isinstance(limited["body"], dict)
     and limited["body"].get("code") == "RATE_LIMITED" and s_other != 429 and s_read == 200,
     {"codes": codes, "429": limited, "otherUser": s_other, "read": s_read})
time.sleep(6)  # let S3's bucket refill

rid = f"st57-{RUN}-request"
s, _ = call("GET", "/api/enrollment/catalog", tok["S2"], headers={"X-Request-Id": rid})
echoed = LAST_HEADERS.get("X-Request-Id", "")
s2, _ = call("GET", "/api/enrollment/catalog", tok["S2"])
generated = LAST_HEADERS.get("X-Request-Id", "")
step("ST-57", "Every request carries an X-Request-Id: the caller's is kept, otherwise one is assigned",
     "same id echoed; a fresh one when none sent", s == 200 and rid in echoed and generated and rid not in generated,
     {"sent": rid, "echoed": echoed, "generated": generated})

s_short, b_short = call("POST", "/api/identity/users", admin,
                        {"name": "Short Pw", "email": f"shortpw-{RUN}@ucms.test", "password": "Short-pw1", "role": "STUDENT"})
long_pw = f"Long-enough-{RUN}!"
s_long, b_long = call("POST", "/api/identity/users", admin,
                      {"name": "Long Pw", "email": f"longpw-{RUN}@ucms.test", "password": long_pw, "role": "STUDENT"})
step("ST-58", "New accounts need a 12+ character password (blank still means a generated one)", "400 / 201 + signs in",
     s_short == 400 and s_long == 201 and login(f"longpw-{RUN}@ucms.test", long_pw) is not None,
     {"short": [s_short, b_short.get("message") if isinstance(b_short, dict) else b_short], "long": s_long})

t_s1 = login(people["S1"]["email"], people["S1"]["password"])
s_email, b_email = call("PATCH", "/api/identity/users/me/password", t_s1,
                        {"currentPassword": people["S1"]["password"], "newPassword": people["S1"]["email"]})
s_hist, b_hist = call("PATCH", "/api/identity/users/me/password", t_s1,
                      {"currentPassword": people["S1"]["password"], "newPassword": people["S1"]["password"]})
msg = lambda b: b.get("message") if isinstance(b, dict) else b
step("ST-59", "Keycloak's password policy refuses the email and a recent password, with its reason (400, not 503)",
     "400 + reason x2", s_email == 400 and s_hist == 400 and "password" in str(msg(b_email)).lower(),
     {"email": [s_email, msg(b_email)], "recent": [s_hist, msg(b_hist)]})

# ---------------------------------------------------------------- broker resilience
RABBIT_AUTH = "ucms_admin:" + env_file()["RABBITMQ_PASSWORD"]
dlq_before = call("GET", "/api/queues/%2F/notification.events.dlq", base=RABBIT, auth=RABBIT_AUTH)[1].get("messages", 0)
bad = {"properties": {}, "routing_key": "enrollment.broken", "payload": "this is not json", "payload_encoding": "string"}
call("POST", "/api/exchanges/%2F/ucms.events/publish", raw=json.dumps(bad).encode(),
     headers={"Content-Type": "application/json"}, base=RABBIT, auth=RABBIT_AUTH)
dlq = wait_for(lambda: call("GET", "/api/queues/%2F/notification.events.dlq", base=RABBIT,
                            auth=RABBIT_AUTH)[1].get("messages", 0) > dlq_before, timeout=20)
step("ST-49", "Malformed event is dead-lettered after retries, not redelivered forever", "+1 in notification.events.dlq",
     bool(dlq), "dead-lettered" if dlq else "not in DLQ after 20s")

passed = sum(r["pass"] for r in results)
print(f"\n{passed}/{len(results)} passed")
if THROTTLED:
    print(f"(rate limit made {len(THROTTLED)} call(s) wait and retry, as a browser would)")
for r in results:
    if isinstance(r["actual"], (bytes, bytearray)):
        r["actual"] = repr(r["actual"])
    elif isinstance(r["actual"], list):
        r["actual"] = [repr(x) if isinstance(x, (bytes, bytearray)) else x for x in r["actual"]]
with open(__file__.replace("system_test.py", "system_test_results.json"), "w") as f:
    json.dump({"run": RUN, "at": datetime.now().isoformat(), "passed": passed, "total": len(results),
               "throttled": THROTTLED, "results": results}, f, indent=2)
