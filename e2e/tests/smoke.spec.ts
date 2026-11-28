import { expect, test } from '../fixtures/test';
import { USERS } from '../fixtures/users';

test('a visitor is asked to sign in', async ({ page }) => {
  await page.goto('/app/dashboard');

  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
});

test('the admin is greeted on their dashboard', async ({ adminPage }) => {
  await adminPage.goto('/app');

  await expect(adminPage.getByRole('heading', { name: `Welcome back, ${USERS.admin.name}` })).toBeVisible();
  await expect(adminPage.getByText(USERS.admin.email)).toBeVisible();
});

test('the lecturer is greeted on their dashboard', async ({ lecturerPage }) => {
  await lecturerPage.goto('/app');

  await expect(lecturerPage.getByRole('heading', { name: `Welcome back, ${USERS.lecturer.name}` })).toBeVisible();
  await expect(lecturerPage.getByText(USERS.lecturer.email)).toBeVisible();
});

test('the student is greeted on their dashboard', async ({ studentPage }) => {
  await studentPage.goto('/app');

  await expect(studentPage.getByRole('heading', { name: `Welcome back, ${USERS.student.name}` })).toBeVisible();
  await expect(studentPage.getByText(USERS.student.email)).toBeVisible();
});
