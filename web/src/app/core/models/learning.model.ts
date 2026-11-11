/**
 * The Learning Hub's nav tree: academic years → their degree programs → the courses
 * (offerings) the signed-in user can actually access. Academic structure serves this
 * shape for staff; enrollment serves the same shape for students, from their own
 * enrollments. See docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 4.
 */
export interface LearningOfferingNode {
  offeringId: string;
  code: string;
  name: string;
  credits: number;
  requirementType: string | null;
  yearOfStudy: number;
  status: string;
  lecturerId: string | null;
  /** Staff tree only. */
  openForEnrollment?: boolean;
  /** Student tree only: ENROLLED / WAITLISTED / COMPLETED. */
  myStatus?: string;
}

export interface LearningProgramNode {
  degreeProgramId: string;
  degreeProgramCode: string;
  degreeProgramName: string;
  offerings: LearningOfferingNode[];
}

export interface LearningYearNode {
  academicYearId: string;
  academicYearName: string;
  academicYearStatus: string;
  programs: LearningProgramNode[];
}

export interface LearningTreeResponse {
  years: LearningYearNode[];
}

export interface OfferingAccessResponse {
  staffView: boolean;
  manage: boolean;
}
