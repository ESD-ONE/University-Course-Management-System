export type AssignmentStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type SubmissionStatus = 'NOT_SUBMITTED' | 'SUBMITTED' | 'GRADED';

export interface AssignmentResponse {
  id: string;
  offeringId: string;
  courseCode: string | null;
  courseName: string | null;
  title: string;
  description: string | null;
  dueDate: string;
  maxMarks: number;
  status: AssignmentStatus;
  briefFileName: string | null;
  totalStudents: number;
  submittedCount: number;
  gradedCount: number;
  mySubmissionStatus: SubmissionStatus;
  mySubmittedAt: string | null;
  myFileName: string | null;
  myMarks: number | null;
  myFeedback: string | null;
}

export interface AssignmentRequest {
  offeringId?: string;
  title: string;
  description: string | null;
  dueDate: string;
  maxMarks: number;
}

export interface SubmissionRow {
  studentId: string;
  studentName: string | null;
  studentNumber: string | null;
  submissionId: string | null;
  fileName: string | null;
  submittedAt: string | null;
  late: boolean;
  status: SubmissionStatus;
  marks: number | null;
  feedback: string | null;
}

export interface MySubmissionResponse {
  submissionId: string;
  fileName: string;
  submittedAt: string;
  status: SubmissionStatus;
  marks: number | null;
  maxMarks: number;
  feedback: string | null;
  gradedAt: string | null;
}

export interface GradeResponse {
  gradeId: string;
  submissionId: string;
  marks: number;
  maxMarks: number;
  feedback: string | null;
  gradedBy: string;
  gradedAt: string;
}

export interface CourseGradeRow {
  offeringId: string;
  courseCode: string | null;
  courseName: string | null;
  credits: number;
  academicYearName: string | null;
  academicYearStatus: string | null;
  semesterName: string | null;
  gradedAssignments: number;
  percentage: number | null;
  letter: string | null;
  points: number | null;
}

export interface GradeHistoryResponse {
  courses: CourseGradeRow[];
  gpa: number | null;
  creditsGraded: number;
}
