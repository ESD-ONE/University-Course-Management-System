// UCMS browser check under the production edge (the web container's nginx on :8091):
// signs in through Keycloak's hosted page, lands in the app, reloads (silent SSO), and
// records every Content-Security-Policy violation the browser reports along the way.
// A CSP that's too strict shows up here, not in curl.
//
//   NODE_PATH=<dir with playwright-core> CHROMIUM=<chrome binary> node csp_browser_test.js
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');

const EDGE = process.env.UCMS_EDGE || 'http://localhost:8091';
const USER = process.env.UCMS_ADMIN || 'admin@ucms.local';
const PASSWORD = process.env.UCMS_ADMIN_PASSWORD || 'ChangeMe123!';
const results = [];
const step = (id, description, expected, ok, actual) => {
  results.push({ id, description, expected, actual, pass: !!ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${description}  ->  ${JSON.stringify(actual)}`);
};

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM, headless: true });
  const context = await browser.newContext();
  const violations = [];
  // Reported by the page itself, so violations inside Keycloak's pages count too.
  await context.exposeBinding('reportCsp', (_src, v) => violations.push(v));
  await context.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (e) =>
      window.reportCsp({ page: location.pathname, directive: e.violatedDirective, blocked: e.blockedURI }));
  });
  const page = await context.newPage();
  const consoleCsp = [];
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) consoleCsp.push(m.text().slice(0, 200)); });

  await page.goto(EDGE + '/');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForSelector('#kc-form-login', { timeout: 30000 });
  step('B-01', 'The Sign in button reaches Keycloak\'s hosted login page', 'Keycloak form', true, new URL(page.url()).pathname);

  await page.fill('#username', USER);
  await page.fill('#password', PASSWORD);
  await page.click('#kc-login');
  await page.waitForSelector('.who b', { timeout: 60000 });
  const name = await page.textContent('.who b');
  step('B-02', 'After signing in, the app shell shows the signed-in user (token exchange + /users/me via the gateway)',
       'user name shown', !!name, { url: new URL(page.url()).pathname, name });

  await page.reload();
  await page.waitForSelector('.who b', { timeout: 60000 });
  step('B-03', 'Reload keeps the session (keycloak-js silent check-sso iframe works under the CSP)',
       'still signed in, no Sign in button', (await page.getByRole('button', { name: /sign in/i }).count()) === 0,
       new URL(page.url()).pathname);

  const font = await page.evaluate(async () => {
    await document.fonts.ready;
    const inter = [...document.fonts].filter((f) => f.family.replace(/"/g, '') === 'Inter');
    return { faces: inter.length, loaded: inter.filter((f) => f.status === 'loaded').map((f) => f.weight) };
  });
  step('B-04', 'Web font (Inter) loads from fonts.gstatic.com under the CSP', 'Inter faces loaded',
       font.loaded.length > 0, font);

  await page.waitForTimeout(2000);
  step('B-05', 'No Content-Security-Policy violation anywhere in the flow', '0 violations',
       violations.length === 0 && consoleCsp.length === 0, { violations, console: consoleCsp });

  const passed = results.filter((r) => r.pass).length;
  console.log(`\n${passed}/${results.length} passed`);
  fs.writeFileSync(path.join(__dirname, 'csp_browser_test_results.json'),
    JSON.stringify({ at: new Date().toISOString(), passed, total: results.length, results }, null, 2));
  await browser.close();
  process.exit(passed === results.length ? 0 : 1);
})().catch(async (e) => { console.error('ERROR', e.message); process.exit(2); });
