// Browser-driven UI test: real Chrome (headless) against ng serve (:4200 → Gateway :8082).
// Logs in through the login form as each role and drives the new screens.
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');

const APP = 'http://localhost:4200';
const API = 'http://localhost:8082';
const SHOTS = path.join(__dirname, 'shots');
fs.mkdirSync(SHOTS, { recursive: true });
const RUN = Math.random().toString(16).slice(2, 8);
const results = [];

function step(id, desc, ok, actual) {
  results.push({ id, desc, pass: !!ok, actual });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${desc}  ->  ${actual}`);
}

async function api(method, p, token, body) {
  const res = await fetch(API + p, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  return { status: res.status, body: json };
}

async function settle(page) {
  // The live notification stream keeps a request open forever, so "networkidle" never
  // happens; wait for the page heading and for loading skeletons to disappear instead.
  await page.waitForSelector('.content h1, .content .page-title', { timeout: 15000 }).catch(() => {});
  await page.waitForFunction(() => !document.querySelector('.content .skeleton'), null, { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function login(page, email, password) {
  await page.goto(`${APP}/login`);
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.click('button[type=submit]');
  await page.waitForURL('**/app/dashboard', { timeout: 15000 });
}

async function visit(page, route, name, mustContain) {
  await page.goto(`${APP}${route}`);
  await settle(page);
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: true });
  const text = await page.innerText('.content');
  const errorShown = /Could not (load|reach)/.test(text);
  const ok = !errorShown && (!mustContain || text.toLowerCase().includes(mustContain.toLowerCase()));
  step(`UI-${name}`, `${route} renders${mustContain ? ` with "${mustContain}"` : ''}`, ok,
    errorShown ? 'error banner shown' : ok ? 'ok' : `missing "${mustContain}"`);
  return text;
}

(async () => {
  // ---------- test data via API ----------
  const admin = (await api('POST', '/api/identity/auth/login', null, { email: 'admin@ucms.local', password: 'ChangeMe123!' })).body.token;
  const mk = async (name, role) => {
    const email = `ui.${role.toLowerCase()}.${RUN}@uni.edu`;
    const r = await api('POST', '/api/identity/users', admin, { name, email, role });
    return { id: r.body.id, email, password: r.body.temporaryPassword };
  };
  const lecturer = await mk(`Dr. UI Lecturer ${RUN}`, 'LECTURER');
  const student = await mk(`UI Student ${RUN}`, 'STUDENT');
  const years = (await api('GET', '/api/academic/academic-years', admin)).body;
  const year = years.find((y) => y.status === 'ACTIVE');
  const programs = (await api('GET', '/api/academic/degree-programs', admin)).body;
  const catalog = (await api('GET', '/api/academic/course-catalog', admin)).body;
  const course = (await api('POST', '/api/academic/courses', admin, {
    catalogCourseId: catalog[2].id, degreeProgramId: programs[0].id, beginDate: new Date().toISOString().slice(0, 10),
    yearOfStudy: 1, capacity: 5, status: 'PUBLISHED', lecturerId: lecturer.id,
  })).body;
  const offering = (await api('POST', `/api/academic/courses/${course.id}/academic-years`, admin, {
    academicYearId: year.id, capacity: 5, status: 'PUBLISHED',
  })).body;
  step('UI-SETUP', 'Test lecturer, student and an open course offering created', offering && offering.id, catalog[2].code);

  const browser = await chromium.launch({
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    headless: true,
  });
  const consoleErrors = [];
  const newPage = async () => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(`${page.url()}: ${m.text()}`); });
    return page;
  };

  // ---------- ADMIN ----------
  const a = await newPage();
  await login(a, 'admin@ucms.local', 'ChangeMe123!');
  await visit(a, '/app/dashboard', 'admin-dashboard', 'Total Students');
  await visit(a, '/app/registrations', 'admin-registrations', 'Recent Registrations');
  await visit(a, '/app/roles', 'admin-roles', 'Scope Assignments');
  await visit(a, '/app/reports', 'admin-reports', 'Report Library');
  await a.locator('tr', { hasText: 'Enrollment Summary' }).getByRole('button', { name: 'Generate' }).click();
  await a.waitForSelector('.toast', { timeout: 10000 });
  step('UI-ADMIN-GENERATE', 'Admin generates a report from the Reports screen', (await a.innerText('.toast')).includes('generated'), await a.innerText('.toast'));
  const sidebar = await a.innerText('.sidebar');
  step('UI-ADMIN-NAV', 'Admin nav has Registrations/Roles/Reports, no "Coming soon"',
    ['Registrations', 'Roles & Permissions', 'Reports'].every((x) => sidebar.includes(x)) && !sidebar.includes('Coming soon'), sidebar.replace(/\n/g, ' | '));

  // Admin registers the student into a program through the UI
  await a.goto(`${APP}/app/registrations`);
  await a.fill('#rgSearch', `UI Student ${RUN}`);
  await a.waitForTimeout(1500);
  await a.selectOption('#rgStudent', { index: 1 });
  await a.getByRole('button', { name: 'Register to Program' }).click();
  await a.waitForSelector('.toast', { timeout: 10000 });
  step('UI-ADMIN-REGISTER', 'Admin registers a student into a program via the Registrations form',
    (await a.innerText('.toast')).includes('registered in'), await a.innerText('.toast'));

  // ---------- LECTURER: create + publish an assignment ----------
  const l = await newPage();
  await login(l, lecturer.email, lecturer.password);
  const lnav = await l.innerText('.sidebar');
  step('UI-LECT-NAV', 'Lecturer nav has Assignments/Grading/Students/Registrations',
    ['Assignments', 'Grading', 'Students', 'Registrations'].every((x) => lnav.includes(x)), lnav.replace(/\n/g, ' | '));
  await visit(l, '/app/dashboard', 'lecturer-dashboard', 'Submissions to Grade');
  await l.goto(`${APP}/app/assignments`);
  await settle(l);
  await l.getByRole('button', { name: '+ Create Assignment' }).click();
  await l.fill('#afTitle', `UI Exercise ${RUN}`);
  await l.fill('#afDesc', 'Implement a calculator.');
  await l.getByRole('button', { name: 'Save' }).click();
  await l.waitForSelector('.toast', { timeout: 10000 });
  await l.locator('tr', { hasText: `UI Exercise ${RUN}` }).getByRole('button', { name: 'Publish' }).click();
  await l.waitForTimeout(1500);
  const lrow = await l.locator('tr', { hasText: `UI Exercise ${RUN}` }).innerText();
  await l.screenshot({ path: path.join(SHOTS, 'lecturer-assignments.png'), fullPage: true });
  step('UI-LECT-ASSIGN', 'Lecturer creates and publishes an assignment through the modal', /Open/.test(lrow), lrow.replace(/\s+/g, ' ').slice(0, 120));

  // ---------- STUDENT: browse, enroll, submit ----------
  const s = await newPage();
  await login(s, student.email, student.password);
  const snav = await s.innerText('.sidebar');
  step('UI-STU-NAV', 'Student nav per wireframe', ['Browse Courses', 'My Enrollments', 'My Program', 'Assignments', 'Grades'].every((x) => snav.includes(x)), snav.replace(/\n/g, ' | '));
  await visit(s, '/app/browse', 'student-browse', catalog[2].code);
  await s.locator('.card', { hasText: `Dr. UI Lecturer ${RUN}` }).getByRole('button', { name: 'Enroll' }).click();
  await s.waitForSelector('.toast', { timeout: 10000 });
  step('UI-STU-ENROLL', 'Student enrolls from Browse Courses', (await s.innerText('.toast')).includes('Enrolled'), await s.innerText('.toast'));
  await visit(s, '/app/my-enrollments', 'student-enrollments', catalog[2].code);
  await visit(s, '/app/my-program', 'student-program', programs[0].name);
  await visit(s, '/app/my-assignments', 'student-assignments', `UI Exercise ${RUN}`);
  await s.locator('.list-card', { hasText: `UI Exercise ${RUN}` }).click();
  await s.setInputFiles('input[type=file]', { name: 'calc.zip', mimeType: 'application/zip', buffer: Buffer.from('PK ui test ' + RUN) });
  await s.getByRole('button', { name: 'Submit Assignment' }).click();
  await s.waitForSelector('.toast', { timeout: 10000 });
  step('UI-STU-SUBMIT', 'Student uploads a submission (file input → MinIO)', (await s.innerText('.toast')).includes('Submitted'), await s.innerText('.toast'));

  // ---------- LECTURER: grade ----------
  await l.goto(`${APP}/app/grading`);
  await settle(l);
  const option = await l.locator('#grAssignment option', { hasText: `UI Exercise ${RUN}` }).getAttribute('value');
  await l.selectOption('#grAssignment', option);
  await l.waitForTimeout(1500);
  const studentRow = l.locator('tr', { hasText: `UI Student ${RUN}` });
  await studentRow.locator('input[type=number]').fill('82');
  await studentRow.locator('input[type=text]').fill('Nicely structured');
  await l.getByRole('button', { name: /Save All Grades/ }).click();
  await l.waitForSelector('.toast', { timeout: 10000 });
  await l.screenshot({ path: path.join(SHOTS, 'lecturer-grading.png'), fullPage: true });
  step('UI-LECT-GRADE', 'Lecturer enters marks and Save All Grades', (await l.innerText('.toast')).includes('saved'), await l.innerText('.toast'));
  await visit(l, `/app/students?offering=${offering.id}`, 'lecturer-roster', `UI Student ${RUN}`);

  // ---------- STUDENT: notification + grade ----------
  await s.waitForTimeout(4000);
  await s.goto(`${APP}/app/grades`);
  await settle(s);
  await s.waitForTimeout(800);
  await s.screenshot({ path: path.join(SHOTS, 'student-grades.png'), fullPage: true });
  const grades = await s.innerText('.content');
  step('UI-STU-GRADES', 'Grade appears on the student Grades screen', grades.includes('82%'), grades.match(/\d+(\.\d+)?% \(\w\)/)?.[0] ?? 'no grade');
  const bellCount = await s.locator('.bell-count').innerText().catch(() => '0');
  await s.click('.bell-btn');
  await s.waitForTimeout(800);
  await s.screenshot({ path: path.join(SHOTS, 'student-notifications.png') });
  const bell = await s.innerText('.bell-dropdown');
  step('UI-STU-BELL', 'Notification bell shows unread count and the grade notification', Number(bellCount) > 0 && bell.includes('Grade posted'), `count ${bellCount}`);
  await visit(s, '/app/dashboard', 'student-dashboard', 'Current GPA');
  await visit(s, '/app/profile', 'student-profile', 'Notification Preferences');
  const profile = await s.innerText('.content');
  step('UI-STU-PROFILE', 'Profile no longer shows the "Not sent yet" disclaimer', !profile.includes('Not sent yet'), 'ok');

  // ---------- logout revokes the token ----------
  const token = await s.evaluate(() => localStorage.getItem('ucms_token'));
  await s.click('.user-btn');
  await s.getByRole('button', { name: 'Log Out' }).click();
  await s.waitForURL('**/login');
  await s.waitForTimeout(1000);
  const after = await api('GET', '/api/identity/users/me', token);
  step('UI-LOGOUT', 'Log Out from the UI revokes the old token at the Gateway', after.status === 401, after.status);

  step('UI-CONSOLE', 'No browser console errors across all screens', consoleErrors.length === 0,
    consoleErrors.length ? consoleErrors.slice(0, 3).join(' || ') : 'none');

  await browser.close();
  const passed = results.filter((r) => r.pass).length;
  console.log(`\n${passed}/${results.length} passed`);
  fs.writeFileSync(path.join(__dirname, 'ui_test_results.json'), JSON.stringify({ run: RUN, passed, total: results.length, results }, null, 2));
})().catch((e) => {
  console.error('UI test crashed:', e.message);
  fs.writeFileSync(path.join(__dirname, 'ui_test_results.json'), JSON.stringify({ crashed: e.message, results }, null, 2));
  process.exit(1);
});
