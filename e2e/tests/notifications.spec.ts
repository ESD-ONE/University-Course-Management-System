import { Api } from '../fixtures/api';
import { accessTokenFrom, signIn } from '../fixtures/login';
import { createAssignment, createOffering, enroll } from '../fixtures/seed';
import { expect, test } from '../fixtures/test';
import { unique } from '../fixtures/users';

/**
 * Live delivery: the event travels from the grading service through RabbitMQ to the
 * notification service, and from there to the student's open browser, with no reload.
 *
 * The student here is created for this test. A shared account would also receive the
 * notifications other tests cause, and the pop-up only shows the latest one.
 */
test('a student is told about a new assignment while the app is open', async ({ api, run, page, request }) => {
  const admin = await api('admin');
  const account = {
    name: `Notified Student ${unique()}`,
    email: `e2e.notified.${unique().toLowerCase()}@ucms.test`,
    password: 'E2e-Notified-Password-1!',
  };
  const student = await admin.post('/api/identity/users', { ...account, role: 'STUDENT', status: 'ACTIVE' });
  await admin.post('/api/enrollment/program-enrollments', {
    studentId: student.id,
    degreeProgramId: run.programId,
    intakeAcademicYearId: run.academicYearId,
    currentYearOfStudy: 1,
  });
  const course = await createOffering(admin, run);

  // The student signs in and leaves the app open. Wait until it is listening for live
  // notifications before anything is sent.
  const listening = page.waitForResponse((response) => response.url().includes('/notifications/stream'));
  const token = await accessTokenFrom(page, () => signIn(page, account));
  await listening;
  await enroll(new Api(request, token), course.id);

  // Meanwhile the lecturer publishes an assignment in the student's course.
  const assignment = await createAssignment(await api('lecturer'), course.id);

  // It pops up on the student's screen without a reload...
  await expect(page.getByText(`New assignment: ${assignment.title}`)).toBeVisible();

  // ...and is kept in their notification list.
  await page.getByRole('button', { name: 'Notifications' }).click();
  await expect(page.getByRole('button', { name: new RegExp(`New assignment: ${assignment.title}`) })).toBeVisible();
});
