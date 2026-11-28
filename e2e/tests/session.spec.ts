import { accessTokenFrom, signIn } from '../fixtures/login';
import { expect, test } from '../fixtures/test';
import { unique } from '../fixtures/users';

/**
 * Signing out must end the session everywhere: in the browser, in Keycloak, and for the
 * token that was already issued. This test uses its own account, because signing out
 * ends that account's session for every test that shares it.
 */
test('after signing out, the user has to sign in again and the old token no longer works', async ({
  api,
  page,
  request,
}) => {
  const account = {
    name: `Session Student ${unique()}`,
    email: `e2e.session.${unique().toLowerCase()}@ucms.test`,
    password: 'E2e-Session-Password-1!',
  };
  await (await api('admin')).post('/api/identity/users', { ...account, role: 'STUDENT', status: 'ACTIVE' });

  // Sign in through the real sign-in page, keeping the token the app was given.
  const token = await accessTokenFrom(page, () => signIn(page, account));
  const withToken = { headers: { Authorization: `Bearer ${token}` } };
  expect((await request.get('/api/identity/users/me', withToken)).status()).toBe(200);

  // Sign out from the user menu.
  await page.getByRole('button', { name: new RegExp(account.name) }).click();
  await page.getByRole('button', { name: 'Log Out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();

  // An app page now asks for sign-in again, without showing any of the app.
  await page.goto('/app/dashboard');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await expect(page.getByRole('navigation')).toHaveCount(0);

  // And the token issued before signing out is rejected.
  expect((await request.get('/api/identity/users/me', withToken)).status()).toBe(401);
});
