import path from 'node:path';

/** The roles the tests sign in as. Only the admin exists in an empty system. */
export type Role = 'admin' | 'setupAdmin' | 'lecturer' | 'student' | 'otherStudent';

export interface TestUser {
  role: Role;
  name: string;
  email: string;
  password: string;
  /** The account role in UCMS. */
  kind: 'ADMIN' | 'LECTURER' | 'STUDENT';
}

/** Throwaway password for the accounts the tests create. Only valid inside the test stack. */
const TEST_PASSWORD = 'E2e-Test-Password-1!';

export const USERS: Record<Role, TestUser> = {
  // Seeded by identity-access-service's first migration (see the root README).
  admin: { role: 'admin', name: 'Seed Administrator', email: 'admin@ucms.local', password: 'ChangeMe123!', kind: 'ADMIN' },
  // A second admin that only creates test data through the API. The gateway rate-limits
  // each user, so setup calls must not eat into the quota of the admin the screens use.
  setupAdmin: { role: 'setupAdmin', name: 'E2E Setup Admin', email: 'e2e.setup.admin@ucms.test', password: TEST_PASSWORD, kind: 'ADMIN' },
  lecturer: { role: 'lecturer', name: 'E2E Lecturer', email: 'e2e.lecturer@ucms.test', password: TEST_PASSWORD, kind: 'LECTURER' },
  student: { role: 'student', name: 'E2E Student', email: 'e2e.student@ucms.test', password: TEST_PASSWORD, kind: 'STUDENT' },
  otherStudent: { role: 'otherStudent', name: 'E2E Other Student', email: 'e2e.other.student@ucms.test', password: TEST_PASSWORD, kind: 'STUDENT' },
};

export const AUTH_DIR = path.join(__dirname, '..', '.auth');

/** Where a role's signed-in browser session is saved by global-setup. */
export function sessionFile(role: Role): string {
  return path.join(AUTH_DIR, `${role}.json`);
}

/** Ids of the accounts and seeded records the specs build on, written by global-setup. */
export const RUN_FILE = path.join(AUTH_DIR, 'run.json');

export interface RunInfo {
  userIds: Record<Role, string>;
  /** The seeded "BSc in Computer Science" program both students are registered in. */
  programId: string;
  /** The seeded active academic year. */
  academicYearId: string;
  departmentId: string;
}

/** A short unique suffix, so data a spec creates never collides with another spec or run. */
export function unique(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36).padStart(2, '0')}`.toUpperCase();
}
