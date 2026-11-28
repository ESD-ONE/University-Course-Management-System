import fs from 'node:fs';
import { chromium, FullConfig, request } from '@playwright/test';
import { Api, ApiError } from './fixtures/api';
import { accessTokenFrom, signIn } from './fixtures/login';
import { AUTH_DIR, Role, RUN_FILE, RunInfo, sessionFile, TestUser, USERS } from './fixtures/users';

/**
 * Runs once before the tests. An empty system has one account, the seeded admin, so this
 * signs in as the admin, creates the other test accounts, registers the two students in
 * a degree program, and saves a signed-in browser session for every role. The tests then
 * start already signed in.
 *
 * It can run again against the same stack: accounts that already exist are reused.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0].use.baseURL!;
  fs.mkdirSync(AUTH_DIR, { recursive: true });

  const browser = await chromium.launch();
  try {
    // 1. The admin signs in; the token the app receives is reused for the setup calls.
    const adminContext = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
    const adminPage = await adminContext.newPage();
    const adminToken = await accessTokenFrom(adminPage, () => signIn(adminPage, USERS.admin));
    await adminContext.storageState({ path: sessionFile('admin') });
    const admin = new Api(await request.newContext({ baseURL, ignoreHTTPSErrors: true }), adminToken);

    // 2. The other accounts.
    const userIds = { admin: (await admin.get('/api/identity/users/me')).id } as Record<Role, string>;
    for (const user of [USERS.setupAdmin, USERS.lecturer, USERS.student, USERS.otherStudent]) {
      userIds[user.role] = await ensureUser(admin, user);
    }

    // 3. Reference records the specs build on, all seeded by the services' migrations.
    const programs = await admin.get('/api/academic/degree-programs?size=100');
    const program = programs.content.find((p: any) => p.code === 'BSc-CS');
    const years = await admin.get('/api/academic/academic-years');
    const year = years.find((y: any) => y.status === 'ACTIVE');
    if (!program || !year) throw new Error('The seeded degree program or active academic year is missing');

    // 4. A student only sees the courses of a program they are registered in.
    for (const role of ['student', 'otherStudent'] as const) {
      await ensureProgramRegistration(admin, userIds[role], program.id, year.id);
    }
    await adminContext.close();

    // 5. A saved session for each of the new accounts.
    for (const user of [USERS.setupAdmin, USERS.lecturer, USERS.student, USERS.otherStudent]) {
      const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
      await signIn(await context.newPage(), user);
      await context.storageState({ path: sessionFile(user.role) });
      await context.close();
    }

    const run: RunInfo = { userIds, programId: program.id, academicYearId: year.id, departmentId: program.departmentId };
    fs.writeFileSync(RUN_FILE, JSON.stringify(run, null, 2));
  } finally {
    await browser.close();
  }
}

/** Creates the account, or finds it when an earlier run already did. Returns its id. */
async function ensureUser(admin: Api, user: TestUser): Promise<string> {
  const found = await admin.get(`/api/identity/users?q=${encodeURIComponent(user.email)}&size=5`);
  const existing = found.content.find((u: any) => u.email === user.email);
  if (existing) return existing.id;
  const created = await admin.post('/api/identity/users', {
    name: user.name,
    email: user.email,
    password: user.password,
    role: user.kind,
    status: 'ACTIVE',
  });
  return created.id;
}

async function ensureProgramRegistration(admin: Api, studentId: string, programId: string, yearId: string) {
  try {
    await admin.post('/api/enrollment/program-enrollments', {
      studentId,
      degreeProgramId: programId,
      intakeAcademicYearId: yearId,
      currentYearOfStudy: 1,
    });
  } catch (error) {
    // Already registered by an earlier run against the same stack.
    if (!(error instanceof ApiError && error.status === 409)) throw error;
  }
}
