export type ProgramEnrollmentStatus = 'ACTIVE' | 'DEFERRED' | 'WITHDRAWN' | 'GRADUATED';
export type CourseEnrollmentStatus = 'ENROLLED' | 'WAITLISTED' | 'DROPPED' | 'COMPLETED';

export interface ProgramEnrollmentResponse {
  id: string;
  studentId: string;
  studentName: string | null;
  studentNumber: string | null;
  degreeProgramId: string;
  degreeProgramCode: string | null;
  degreeProgramName: string | null;
  durationYears: number | null;
  totalCredits: number | null;
  intakeAcademicYearId: string;
  intakeAcademicYearName: string | null;
  currentYearOfStudy: number;
  status: ProgramEnrollmentStatus;
  createdAt: string;
}

export interface CreateProgramEnrollmentRequest {
  studentId: string;
  degreeProgramId: string;
  intakeAcademicYearId: string;
  currentYearOfStudy: number;
}

export interface CourseEnrollmentResponse {
  id: string;
  studentId: string;
  studentName: string | null;
  studentNumber: string | null;
  studentEmail: string | null;
  studentProgramName: string | null;
  studentYearOfStudy: number | null;
  offeringId: string;
  courseCode: string | null;
  courseName: string | null;
  credits: number | null;
  requirementType: string | null;
  academicYearName: string | null;
  semesterName: string | null;
  lecturerId: string | null;
  lecturerName: string | null;
  enrolledOn: string;
  status: CourseEnrollmentStatus;
}

export interface EnrollRequest {
  offeringId: string;
  studentId?: string;
  joinWaitlistIfFull?: boolean;
}

export interface CatalogEntry {
  offeringId: string;
  code: string;
  name: string;
  credits: number;
  requirementType: string | null;
  degreeProgramId: string;
  degreeProgramCode: string;
  degreeProgramName: string;
  yearOfStudy: number;
  academicYearId: string;
  academicYearName: string;
  semesterName: string | null;
  lecturerId: string | null;
  lecturerName: string | null;
  capacity: number;
  enrolled: number;
  waitlisted: number;
  seatsLeft: number;
  openForEnrollment: boolean;
  myStatus: CourseEnrollmentStatus | null;
  myEnrollmentId: string | null;
}

export interface SeatCount {
  offeringId: string;
  enrolled: number;
  waitlisted: number;
}

export interface RegistrationResponse {
  id: string;
  studentId: string;
  studentName: string | null;
  studentNumber: string | null;
  targetType: 'PROGRAM' | 'COURSE';
  targetId: string;
  targetLabel: string | null;
  actorId: string;
  actorName: string;
  actorRole: string;
  action: string;
  createdAt: string;
}

export interface MyStudentResponse {
  studentId: string;
  name: string | null;
  studentNumber: string | null;
  email: string | null;
  programName: string | null;
  yearOfStudy: number | null;
  courseCodes: string[];
  offeringIds: string[];
}

/** Readable label for a registration-log action. */
export const REGISTRATION_ACTION_LABELS: Record<string, string> = {
  PROGRAM_REGISTER: 'Registered in program',
  PROGRAM_UPDATE: 'Program record updated',
  COURSE_ENROLL: 'Enrolled',
  COURSE_WAITLIST: 'Joined waitlist',
  COURSE_DROP: 'Dropped',
  WAITLIST_PROMOTED: 'Promoted from waitlist',
};

export interface StudentOption {
  id: string;
  name: string;
  studentNumber: string | null;
  email: string;
  activeProgramId: string | null;
  activeProgramName: string | null;
}
