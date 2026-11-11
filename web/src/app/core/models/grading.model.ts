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
  weekId: string | null;
  weightPercent: number | null;
  /** While true, myMarks/myFeedback are withheld from the student view even if graded. */
  gradesHidden: boolean;
}

export interface AssignmentRequest {
  offeringId?: string;
  title: string;
  description: string | null;
  dueDate: string;
  maxMarks: number;
  weekId?: string | null;
  weightPercent?: number | null;
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

export interface StudentScoreRow {
  studentId: string;
  studentName: string | null;
  marks: number;
  percentage: number;
}

export interface AssignmentAnalyticsResponse {
  totalStudents: number;
  submitted: number;
  onTime: number;
  late: number;
  graded: number;
  average: number | null;
  median: number | null;
  min: number | null;
  max: number | null;
  /** 10 buckets, 0-10% through 90-100%. */
  histogram: number[];
  topPerformers: StudentScoreRow[];
  needsAttention: StudentScoreRow[];
}

export interface AssignmentTrendPoint {
  assignmentId: string;
  title: string;
  dueDate: string;
  classAverage: number | null;
  submitted: number;
  graded: number;
  totalStudents: number;
  weightPercent: number | null;
}

export interface AtRiskRow {
  studentId: string;
  studentName: string | null;
  averagePercentage: number;
  missingSubmissions: number;
}

export interface OfferingAnalyticsResponse {
  assignments: AssignmentTrendPoint[];
  weightedCourseAverage: number | null;
  classAverage: number | null;
  atRisk: AtRiskRow[];
}
