import fs from 'node:fs';
import { Page, test as base } from '@playwright/test';
import { Api, apiFor } from './api';
import { Role, RUN_FILE, RunInfo, sessionFile } from './users';

type ApiRole = Exclude<Role, 'setupAdmin'>;

interface Fixtures {
  /** A browser page already signed in as that role. */
  adminPage: Page;
  lecturerPage: Page;
  studentPage: Page;
  otherStudentPage: Page;
  /**
   * The API as a role, for creating the data a test needs before it uses the screens.
   * `api('admin')` is the setup admin, a separate account from the one `adminPage` is
   * signed in as, so that setup calls and screens do not share one rate-limit quota.
   */
  api: (role: ApiRole) => Promise<Api>;
  /** Ids of the accounts and seeded records created once per run. */
  run: RunInfo;
}

export const test = base.extend<Fixtures>({
  adminPage: async ({ browser, baseURL }, use) => useSignedInPage(browser, 'admin', baseURL!, use),
  lecturerPage: async ({ browser, baseURL }, use) => useSignedInPage(browser, 'lecturer', baseURL!, use),
  studentPage: async ({ browser, baseURL }, use) => useSignedInPage(browser, 'student', baseURL!, use),
  otherStudentPage: async ({ browser, baseURL }, use) => useSignedInPage(browser, 'otherStudent', baseURL!, use),

  api: async ({ browser, baseURL }, use) => {
    const clients = new Map<ApiRole, Promise<Api>>();
    await use((role) => {
      if (!clients.has(role)) {
        clients.set(role, apiFor(browser, role === 'admin' ? 'setupAdmin' : role, baseURL!));
      }
      return clients.get(role)!;
    });
  },

  run: async ({}, use) => {
    await use(JSON.parse(fs.readFileSync(RUN_FILE, 'utf8')) as RunInfo);
  },
});

async function useSignedInPage(
  browser: import('@playwright/test').Browser,
  role: Role,
  baseURL: string,
  use: (page: Page) => Promise<void>,
): Promise<void> {
  const context = await browser.newContext({ storageState: sessionFile(role), baseURL });
  const page = await context.newPage();
  await use(page);
  await context.close();
}

export { expect } from '@playwright/test';
