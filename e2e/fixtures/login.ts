import { Page, expect } from '@playwright/test';
import { TestUser } from './users';

/** Signs in through the app's own flow: the UCMS login screen, then Keycloak's form. */
export async function signIn(page: Page, user: Pick<TestUser, 'email' | 'password'>): Promise<void> {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Sign in' }).click();
  // Keycloak's page moves focus to the username field while it loads. If that happens
  // in the middle of typing, the text lands in the wrong field, so type until both
  // fields hold what was meant for them.
  const username = page.getByLabel('Username or email');
  const password = page.getByLabel('Password', { exact: true });
  await expect(async () => {
    await username.fill(user.email);
    await password.fill(user.password);
    await expect(username).toHaveValue(user.email, { timeout: 1_000 });
    await expect(password).toHaveValue(user.password, { timeout: 1_000 });
  }).toPass();
  await page.getByRole('button', { name: 'Sign In' }).click();
  // The dashboard greets the signed-in user by name. Signing in is several redirects and
  // the first requests to the services, so it gets extra time.
  await expect(page.getByRole('heading', { name: /^Welcome back, / })).toBeVisible({ timeout: 30_000 });
}

/**
 * The access token the app receives when it starts with a signed-in session. The web
 * client only issues tokens through the browser sign-in, so this is how a test gets one
 * for calling the API directly.
 */
export async function accessTokenFrom(page: Page, open: () => Promise<unknown>): Promise<string> {
  const [response] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/protocol/openid-connect/token') && r.request().method() === 'POST' && r.ok(),
    ),
    open(),
  ]);
  return (await response.json()).access_token as string;
}
