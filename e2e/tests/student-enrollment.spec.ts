import { Page } from '@playwright/test';
import { createOffering, Offering } from '../fixtures/seed';
import { expect, test } from '../fixtures/test';

/**
 * A course with one seat: the first student takes it, the second joins the waitlist, and
 * when the first drops the second is enrolled automatically.
 */
test('a student enrolls, a second is waitlisted, and is promoted when the first drops', async ({
  api,
  run,
  studentPage,
  otherStudentPage,
}) => {
  const course = await createOffering(await api('admin'), run, { capacity: 1 });

  // The first student takes the only seat.
  await findCourse(studentPage, course);
  await studentPage.getByRole('button', { name: 'Enroll', exact: true }).click();
  await expect(studentPage.getByRole('button', { name: 'Enrolled ✓' })).toBeVisible();
  await expect(studentPage.getByText('Full', { exact: true })).toBeVisible();

  await studentPage.goto('/app/my-enrollments');
  await expect(enrollmentRow(studentPage, course)).toContainText('Enrolled');

  // The second student finds it full and joins the waitlist.
  await findCourse(otherStudentPage, course);
  await otherStudentPage.getByRole('button', { name: 'Join Waitlist' }).click();
  await expect(otherStudentPage.getByRole('button', { name: 'Waitlisted' })).toBeVisible();

  await otherStudentPage.goto('/app/my-enrollments');
  await expect(enrollmentRow(otherStudentPage, course)).toContainText('Waitlisted');

  // The first student drops the course.
  await enrollmentRow(studentPage, course).getByRole('button', { name: 'Drop' }).click();
  await studentPage.getByRole('button', { name: 'Confirm' }).click();
  await expect(enrollmentRow(studentPage, course)).toHaveCount(0);

  // The seat goes to the waitlisted student without them doing anything.
  await otherStudentPage.reload();
  await expect(enrollmentRow(otherStudentPage, course)).toContainText('Enrolled');
});

/** Opens Browse Courses and narrows the list to the one course. */
async function findCourse(page: Page, course: Offering): Promise<void> {
  await page.goto('/app/browse');
  await page.getByLabel('Course', { exact: true }).fill(course.code);
  await expect(page.getByText(course.name)).toBeVisible();
}

function enrollmentRow(page: Page, course: Offering) {
  return page.getByRole('row').filter({ hasText: course.code });
}
