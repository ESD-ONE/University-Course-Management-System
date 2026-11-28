import { defineConfig, devices } from '@playwright/test';

/**
 * The tests run against the stack started by infra/e2e-up.sh. Set E2E_BASE_URL when it is
 * on another port, for example E2E_BASE_URL=https://localhost:8191.
 */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'https://localhost:8091';

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // A few at a time: every worker signs in and creates data through the same gateway,
  // which rate-limits each user as it does in production.
  workers: 3,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    // The stack serves HTTPS with a certificate made for it by e2e-up.sh.
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
});
