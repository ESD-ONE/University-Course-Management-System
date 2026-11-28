import { createOffering, enroll } from '../fixtures/seed';
import { expect, test } from '../fixtures/test';
import { chooseFromRowMenu } from '../fixtures/ui';
import { unique, USERS } from '../fixtures/users';

/**
 * The whole life of one assignment, across both roles: the lecturer creates and publishes
 * it, the student submits a file, the lecturer grades it, and the student sees the result.
 */
test('an assignment is published, submitted, graded and the student sees the grade', async ({
  api,
  run,
  lecturerPage,
  studentPage,
}) => {
  const course = await createOffering(await api('admin'), run);
  await enroll(await api('student'), course.id);
  const title = `Essay ${unique()}`;
  // Unique, so a row showing it can only be this test's submission.
  const fileName = `essay-${unique().toLowerCase()}.txt`;

  // The lecturer creates the assignment. It starts as a draft.
  await lecturerPage.goto('/app/assignments');
  await lecturerPage.getByRole('button', { name: '+ Create Assignment' }).click();
  const form = lecturerPage.locator('form').filter({ hasText: 'Instructions' });
  await form.getByLabel('Course').selectOption(course.id);
  await form.getByLabel('Title').fill(title);
  await form.getByLabel('Instructions').fill('Write 500 words on testing.');
  await form.getByLabel('Due').fill(inSevenDays());
  await form.getByLabel('Max marks').fill('100');
  await form.getByRole('button', { name: 'Create assignment' }).click();

  // The lecturer may have many assignments; narrow the list to this one, as they would.
  await lecturerPage.getByRole('textbox', { name: 'Assignment' }).fill(title);
  const assignmentRow = lecturerPage.getByRole('row').filter({ hasText: title });
  await expect(assignmentRow).toContainText('Draft');

  // Publishing makes it visible to students.
  await chooseFromRowMenu(lecturerPage, assignmentRow, `Actions for ${title}`, { role: 'button', name: 'Publish' });
  await expect(assignmentRow).toContainText('Open');

  // The student opens it and submits a file.
  await studentPage.goto('/app/my-assignments');
  await studentPage.getByRole('button', { name: title }).click();
  await expect(studentPage.getByRole('heading', { name: title })).toBeVisible();
  const [fileChooser] = await Promise.all([
    studentPage.waitForEvent('filechooser'),
    studentPage.getByText('Choose a file to upload').click(),
  ]);
  await fileChooser.setFiles({ name: fileName, mimeType: 'text/plain', buffer: Buffer.from('My essay.') });
  await studentPage.getByRole('button', { name: 'Submit assignment' }).click();
  await expect(studentPage.getByRole('button', { name: 'Replace submission' })).toBeVisible();
  await expect(studentPage.getByText(fileName, { exact: true })).toBeVisible();

  // The lecturer opens the submissions from the assignment and grades the student's work.
  await chooseFromRowMenu(lecturerPage, assignmentRow, `Actions for ${title}`, { role: 'link', name: 'View submissions' });
  await expect(lecturerPage.getByRole('heading', { name: 'Grade Submissions' })).toBeVisible();
  const submissionRow = lecturerPage.getByRole('row').filter({ hasText: USERS.student.name });
  await expect(submissionRow).toContainText(fileName);
  await lecturerPage.getByLabel(`Marks for ${USERS.student.name}`).fill('85');
  await lecturerPage.getByLabel(`Feedback for ${USERS.student.name}`).fill('Clear and well argued.');
  await lecturerPage.getByRole('button', { name: /^Save All Grades/ }).click();
  await expect(submissionRow).toContainText('Graded');

  // The student sees the marks and feedback on the assignment...
  await studentPage.reload();
  await studentPage.getByRole('button', { name: title }).click();
  // Exact, because the student may also still see the live "you scored 85 / 100" pop-up.
  await expect(studentPage.getByText('85 / 100', { exact: true })).toBeVisible();
  await expect(studentPage.getByText('Clear and well argued.')).toBeVisible();

  // ...and the course grade on their grades page.
  await studentPage.goto('/app/grades');
  await expect(studentPage.getByRole('row').filter({ hasText: course.code })).toContainText('85% (A)');
});

/** A value for a datetime-local field, a week from now. */
function inSevenDays(): string {
  const date = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
