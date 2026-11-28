import { expect, test } from '../fixtures/test';
import { unique } from '../fixtures/users';

/** The setup work an admin does before a term: accounts, reference data and courses. */

test('the admin creates a student account', async ({ adminPage }) => {
  const suffix = unique();
  const name = `New Student ${suffix}`;
  const email = `e2e.new.${suffix.toLowerCase()}@ucms.test`;

  await adminPage.goto('/app/users');
  await adminPage.getByRole('tab', { name: /^Students/ }).click();
  await adminPage.getByRole('button', { name: '+ New Student' }).click();
  await adminPage.getByLabel('Full Name').fill(name);
  await adminPage.getByLabel('Email').fill(email);
  await adminPage.getByRole('button', { name: 'Create Student' }).click();

  // The admin is shown a one-time password to hand over.
  await expect(adminPage.getByRole('heading', { name: 'Student Created' })).toBeVisible();
  await expect(adminPage.getByText('One-time temporary password')).toBeVisible();
  await adminPage.getByRole('button', { name: 'Done' }).click();

  // The account is in the list with a student ID, and still there after a reload.
  await adminPage.reload();
  await adminPage.getByRole('tab', { name: /^Students/ }).click();
  await adminPage.getByLabel('Search').fill(email);
  const row = adminPage.getByRole('row').filter({ hasText: email });
  await expect(row).toContainText(name);
  await expect(row).toContainText(/STU-\d{4}-\d{4}/);
});

test('the admin adds a faculty and a department in it', async ({ adminPage }) => {
  const suffix = unique();
  const faculty = { name: `Faculty of Testing ${suffix}`, code: `FT${suffix}` };
  const department = { name: `Department of Testing ${suffix}`, code: `DT${suffix}` };

  await adminPage.goto('/app/reference-data');
  await adminPage.getByRole('button', { name: '+ Add Entry' }).click();
  const form = adminPage.locator('form');
  await form.getByLabel('Name').fill(faculty.name);
  await form.getByLabel('Short Code').fill(faculty.code);
  await form.getByRole('button', { name: 'Add Entry', exact: true }).click();
  await adminPage.getByLabel('Search').fill(faculty.name);
  await expect(adminPage.getByRole('row').filter({ hasText: faculty.name })).toContainText(faculty.code);

  await adminPage.getByRole('tab', { name: 'Departments' }).click();
  await adminPage.getByRole('button', { name: '+ Add Entry' }).click();
  await form.getByLabel('Name').fill(department.name);
  await form.getByLabel('Short Code').fill(department.code);
  await form.getByLabel('Faculty').fill(faculty.name);
  await adminPage.getByRole('option', { name: faculty.name }).click();
  await form.getByRole('button', { name: 'Add Entry', exact: true }).click();

  // The department is listed under its faculty...
  const departmentRow = adminPage.getByRole('row').filter({ hasText: department.name });
  await adminPage.getByLabel('Search').fill(department.name);
  await expect(departmentRow).toContainText(faculty.name);

  // ...and still there after a reload.
  await adminPage.reload();
  await adminPage.getByRole('tab', { name: 'Departments' }).click();
  await adminPage.getByLabel('Search').fill(department.name);
  await expect(departmentRow).toContainText(faculty.name);
});

test('the admin creates and publishes a course', async ({ adminPage }) => {
  const suffix = unique();
  const course = { code: `ADM${suffix}`, name: `Admin course ${suffix}` };

  await adminPage.goto('/app/courses/create');
  await adminPage.getByLabel('Course Code').fill(course.code);
  await adminPage.getByLabel('Course Name').fill(course.name);
  await adminPage.getByLabel('Credits').fill('3');
  await adminPage.getByLabel('Degree Program').fill('BSc in Computer Science');
  await adminPage.getByRole('option', { name: /BSc in Computer Science/ }).click();
  await adminPage.getByLabel('Begin Date').fill('2025-09-01');
  await adminPage.getByLabel('Year of Study').fill('1');
  await adminPage.getByLabel('Enrollment Capacity').fill('25');
  await adminPage.getByRole('button', { name: 'Publish Course' }).click();

  // Saving returns to the course list, where the new course is shown as published.
  await expect(adminPage.getByRole('heading', { name: 'Courses', exact: true })).toBeVisible();
  await adminPage.getByLabel('Course', { exact: true }).fill(course.code);
  const row = adminPage.getByRole('row').filter({ hasText: course.code });
  await expect(row).toContainText(course.name);
  await expect(row).toContainText('PUBLISHED');
});
