import { Api } from './api';
import { RunInfo, unique } from './users';

/**
 * Creates the data a test needs through the API, so the test itself only uses the
 * screens it is about. Everything gets a unique code or title: tests share one system
 * and must not see each other's data.
 */

export interface Offering {
  id: string;
  code: string;
  name: string;
}

/**
 * A published course in the seeded degree program, offered in the active academic year
 * and open for enrollment. Taught by the test lecturer.
 */
export async function createOffering(
  admin: Api,
  run: RunInfo,
  options: { capacity?: number } = {},
): Promise<Offering> {
  const suffix = unique();
  const code = `E2E${suffix}`;
  const name = `End-to-end course ${suffix}`;
  const capacity = options.capacity ?? 30;

  const catalogCourse = await admin.post('/api/academic/course-catalog', {
    code,
    name,
    defaultCredits: 3,
    owningDepartmentId: run.departmentId,
  });
  await admin.post(`/api/academic/degree-programs/${run.programId}/curriculum`, {
    catalogCourseId: catalogCourse.id,
    requirementType: 'CORE',
    recommendedYear: 1,
    recommendedSemester: 1,
    creditValue: 3,
  });
  const course = await admin.post('/api/academic/courses', {
    catalogCourseId: catalogCourse.id,
    degreeProgramId: run.programId,
    beginDate: '2025-09-01',
    yearOfStudy: 1,
    capacity,
    status: 'PUBLISHED',
    lecturerId: run.userIds.lecturer,
  });
  // No semester: an offering tied to a semester is only open inside that semester's
  // enrollment window, and the seeded windows are in the past.
  const offering = await admin.post(`/api/academic/courses/${course.id}/academic-years`, {
    academicYearId: run.academicYearId,
    status: 'PUBLISHED',
  });
  return { id: offering.id, code, name };
}

/** Enrolls the calling student in the offering. */
export async function enroll(student: Api, offeringId: string): Promise<void> {
  await student.post('/api/enrollment/course-enrollments', { offeringId, joinWaitlistIfFull: false });
}

export interface Assignment {
  id: string;
  title: string;
}

/** An assignment in the offering, due in a week. Published unless told otherwise. */
export async function createAssignment(
  lecturer: Api,
  offeringId: string,
  options: { publish?: boolean } = {},
): Promise<Assignment> {
  const title = `Assignment ${unique()}`;
  const assignment = await lecturer.post('/api/grading/assignments', {
    offeringId,
    title,
    description: 'Created by an end-to-end test.',
    dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    maxMarks: 100,
  });
  if (options.publish ?? true) {
    await publishAssignment(lecturer, assignment.id);
  }
  return { id: assignment.id, title };
}

export async function publishAssignment(lecturer: Api, assignmentId: string): Promise<void> {
  await lecturer.patch(`/api/grading/assignments/${assignmentId}/status`, { status: 'OPEN' });
}
