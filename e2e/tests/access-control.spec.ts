import { Page } from '@playwright/test';
import { ApiError } from '../fixtures/api';
import { expect, test } from '../fixtures/test';

/** What each role is offered, and what happens when a role reaches for something else. */

test('the admin menu offers administration', async ({ adminPage }) => {
  await adminPage.goto('/app');

  await expectMenu(adminPage, { has: ['Users', 'Roles & Permissions', 'Reference Data', 'Reports'], lacks: ['Browse Courses', 'My Courses'] });
});

test('the lecturer menu offers teaching, not administration', async ({ lecturerPage }) => {
  await lecturerPage.goto('/app');

  await expectMenu(lecturerPage, { has: ['My Courses', 'Assignments', 'Grading'], lacks: ['Users', 'Reports', 'Browse Courses'] });
});

test('the student menu offers studying, not teaching or administration', async ({ studentPage }) => {
  await studentPage.goto('/app');

  await expectMenu(studentPage, { has: ['Browse Courses', 'My Enrollments', 'Grades'], lacks: ['Users', 'Grading', 'Reports'] });
});

test('a student who types the address of the users page is sent back to the dashboard', async ({ studentPage }) => {
  await studentPage.goto('/app/users');

  await expect(studentPage).toHaveURL(/\/app\/dashboard$/);
  await expect(studentPage.getByRole('heading', { name: 'Users' })).toHaveCount(0);
});

test('a lecturer who types the address of the reports page is sent back to the dashboard', async ({ lecturerPage }) => {
  await lecturerPage.goto('/app/reports');

  await expect(lecturerPage).toHaveURL(/\/app\/dashboard$/);
  await expect(lecturerPage.getByRole('heading', { name: 'Reports' })).toHaveCount(0);
});

test('a student who calls the user list directly is refused by the server', async ({ api }) => {
  const student = await api('student');

  const error = await student.get('/api/identity/users').catch((e) => e);

  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(403);
});

async function expectMenu(page: Page, items: { has: string[]; lacks: string[] }): Promise<void> {
  const menu = page.getByRole('navigation');
  for (const name of items.has) {
    await expect(menu.getByRole('link', { name, exact: true })).toBeVisible();
  }
  for (const name of items.lacks) {
    await expect(menu.getByRole('link', { name, exact: true })).toHaveCount(0);
  }
}
