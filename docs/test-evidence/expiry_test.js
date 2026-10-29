// Auto-logout on token expiry. Run with identity-access started with JWT_EXPIRATION_MINUTES=1.
const { chromium } = require('playwright-core');

const APP = 'http://localhost:4200';
const API = 'http://localhost:8082';
const results = [];
const step = (id, desc, ok, actual) => {
  results.push({ id, desc, pass: !!ok, actual });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${desc}  ->  ${actual}`);
};
const claims = (t) => JSON.parse(Buffer.from(t.split('.')[1], 'base64url').toString());

async function login(page) {
  await page.goto(`${APP}/login`);
  await page.fill('#email', 'admin@ucms.local');
  await page.fill('#password', 'ChangeMe123!');
  await page.click('button[type=submit]');
  await page.waitForURL('**/app/dashboard', { timeout: 15000 });
  return page.evaluate(() => localStorage.getItem('ucms_token'));
}

async function expiredBanner(page) {
  return (await page.locator('.login-error').innerText().catch(() => '')).includes('session has expired');
}

(async () => {
  const browser = await chromium.launch({
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    headless: true,
  });

  // 1. Idle on a page: the timer signs the user out at exp.
  const p1 = await (await browser.newContext()).newPage();
  const token1 = await login(p1);
  const c = claims(token1);
  step('EXP-1', 'token lifetime matches JWT_EXPIRATION_MINUTES=1', c.exp - c.iat === 60, `${c.exp - c.iat}s`);
  const waitMs = c.exp * 1000 - Date.now() + 3000;
  await p1.waitForURL('**/login?expired=1', { timeout: waitMs + 10000 });
  step('EXP-2', 'idle user is redirected to /login?expired=1 at expiry', true, p1.url());
  step('EXP-3', 'login page explains the session expired', await expiredBanner(p1), await p1.locator('.login-error').innerText().catch(() => 'no banner'));
  const left = await p1.evaluate(() => localStorage.getItem('ucms_token'));
  step('EXP-4', 'token removed from storage', left === null, String(left));
  await p1.goto(`${APP}/app/dashboard`);
  await p1.waitForURL('**/login**');
  step('EXP-5', 'protected route is no longer reachable', p1.url().includes('/login'), p1.url());

  // 2. Refresh / reopen with an already-expired token in storage.
  const ctx2 = await browser.newContext();
  await ctx2.addInitScript((t) => { if (!sessionStorage.getItem('seeded')) { localStorage.setItem('ucms_token', t); sessionStorage.setItem('seeded', '1'); } }, token1);
  const p2 = await ctx2.newPage();
  await p2.goto(`${APP}/app/dashboard`);
  await p2.waitForURL('**/login**', { timeout: 15000 });
  await p2.waitForTimeout(500);
  step('EXP-6', 'reopening with an expired token lands on /login?expired=1', p2.url().includes('expired=1'), p2.url());
  step('EXP-7', 'expired message shown after refresh', await expiredBanner(p2), 'banner');

  // 3. Token rejected by the server mid-session (revoked by a logout elsewhere) -> 401 -> sign out.
  const p3 = await (await browser.newContext()).newPage();
  const token3 = await login(p3);
  const r = await fetch(`${API}/api/identity/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${token3}` } });
  await p3.click('a[href="/app/users"]');
  await p3.waitForURL('**/login?expired=1', { timeout: 15000 }).catch(() => {});
  step('EXP-8', 'a 401 on an API call signs the user out', p3.url().includes('/login?expired=1'), `logout ${r.status}, now at ${p3.url()}`);

  await browser.close();
  const failed = results.filter((x) => !x.pass).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  require('fs').writeFileSync(__dirname + '/expiry_test_results.json', JSON.stringify(results, null, 2));
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
