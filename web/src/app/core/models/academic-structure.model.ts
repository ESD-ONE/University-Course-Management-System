export type AcademicYearStatus = 'UPCOMING' | 'ACTIVE' | 'ARCHIVED';
export type SemesterStatus = 'UPCOMING' | 'ACTIVE' | 'ARCHIVED';
export type ProgramStatus = 'ACTIVE' | 'INACTIVE';
export type RequirementType = 'CORE' | 'ELECTIVE';
export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
export type DegreeProgramYearStatus = 'PLANNED' | 'ACTIVE' | 'DISCONTINUED';

export interface SemesterResponse {
  id: string;
  name: string;
  sequence: number;
  enrollmentOpenAt: string | null;
  enrollmentCloseAt: string | null;
  status: SemesterStatus;
}

export interface AcademicYearResponse {
  id: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: AcademicYearStatus;
  semesters: SemesterResponse[];
}

export interface CreateAcademicYearRequest {
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  status: AcademicYearStatus;
  createDefaultSemesters: boolean;
}

export interface UpdateAcademicYearRequest {
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  status: AcademicYearStatus;
}

export interface CreateSemesterRequest {
  name: string;
  sequence: number;
  enrollmentOpenAt?: string | null;
  enrollmentCloseAt?: string | null;
  status: SemesterStatus;
}

export interface UpdateSemesterRequest {
  name: string;
  sequence: number;
  enrollmentOpenAt?: string | null;
  enrollmentCloseAt?: string | null;
  status: SemesterStatus;
}

export interface DegreeProgramResponse {
  id: string;
  code: string;
  name: string;
  departmentId: string;
  durationYears: number;
  totalCredits: number;
  status: ProgramStatus;
}

export interface CreateDegreeProgramRequest {
  code: string;
  name: string;
  departmentId: string;
  durationYears: number;
  totalCredits: number;
  status: ProgramStatus;
}

export interface UpdateDegreeProgramRequest {
  name: string;
  durationYears: number;
  totalCredits: number;
  status: ProgramStatus;
}

export interface ProgramCourseResponse {
  id: string;
  catalogCourseId: string;
  catalogCourseCode: string;
  catalogCourseName: string;
  requirementType: RequirementType;
  recommendedYear: number;
  recommendedSemester: number;
  creditValue: number;
}

export interface CreateProgramCourseRequest {
  catalogCourseId: string;
  requirementType: RequirementType;
  recommendedYear: number;
  recommendedSemester: number;
  creditValue: number;
}

export interface CourseCatalogResponse {
  id: string;
  code: string;
  name: string;
  defaultCredits: number;
  owningDepartmentId: string;
}

export interface CreateCourseCatalogRequest {
  code: string;
  name: string;
  defaultCredits: number;
  owningDepartmentId: string;
}

export interface CourseResponse {
  id: string;
  catalogCourseId: string;
  catalogCourseCode: string;
  catalogCourseName: string;
  degreeProgramId: string;
  degreeProgramCode: string;
  degreeProgramName: string;
  beginDate: string;
  yearOfStudy: number;
  lecturerId: string;
  capacity: number;
  status: CourseStatus;
}

export interface CreateCourseRequest {
  catalogCourseId: string;
  degreeProgramId: string;
  beginDate: string;
  yearOfStudy: number;
  capacity: number;
  status: CourseStatus;
  lecturerId?: string | null;
}

export interface UpdateCourseRequest {
  beginDate: string;
  yearOfStudy: number;
  capacity: number;
  status: CourseStatus;
  lecturerId?: string | null;
}

export interface CourseAcademicYearResponse {
  id: string;
  courseId: string;
  catalogCourseCode: string;
  catalogCourseName: string;
  degreeProgramId: string;
  beginDate: string;
  yearOfStudy: number;
  academicYearId: string;
  academicYearName: string;
  semesterId: string | null;
  semesterName: string | null;
  capacity: number;
  /** Resolved: this year's override if set, else the course template's own lecturer. */
  lecturerId: string;
  status: CourseStatus;
}

export interface CreateCourseAcademicYearRequest {
  academicYearId: string;
  semesterId?: string | null;
  capacity?: number | null;
  /** Who teaches this course this year — omit to use the course template's default. */
  lecturerId?: string | null;
  status: CourseStatus;
}

export interface UpdateCourseAcademicYearRequest {
  semesterId?: string | null;
  capacity?: number | null;
  lecturerId?: string | null;
  status: CourseStatus;
}

export interface DegreeProgramAcademicYearResponse {
  id: string;
  degreeProgramId: string;
  degreeProgramCode: string;
  degreeProgramName: string;
  academicYearId: string;
  academicYearName: string;
  durationYears: number;
  totalCredits: number;
  status: DegreeProgramYearStatus;
}

export interface CreateDegreeProgramAcademicYearRequest {
  academicYearId: string;
  durationYears?: number | null;
  totalCredits?: number | null;
  status: DegreeProgramYearStatus;
  /** Which of the program's courses to also assign to this year. Omit/null = every
   * course currently in the program (the "select all" default); an explicit array
   * (including []) assigns exactly those. */
  courseIds?: string[] | null;
}

export interface UpdateDegreeProgramAcademicYearRequest {
  durationYears?: number | null;
  totalCredits?: number | null;
  status: DegreeProgramYearStatus;
}

/** One course offering (a course's run in a specific academic year), resolved —
 *  academic-structure-service's GET /course-offerings. */
export interface CourseOfferingResponse {
  id: string;
  courseId: string;
  catalogCourseId: string;
  code: string;
  name: string;
  credits: number;
  requirementType: string | null;
  degreeProgramId: string;
  degreeProgramCode: string;
  degreeProgramName: string;
  departmentId: string;
  yearOfStudy: number;
  academicYearId: string;
  academicYearName: string;
  academicYearStatus: string;
  semesterId: string | null;
  semesterName: string | null;
  enrollmentOpenAt: string | null;
  enrollmentCloseAt: string | null;
  capacity: number;
  lecturerId: string | null;
  status: CourseStatus;
  openForEnrollment: boolean;
}
