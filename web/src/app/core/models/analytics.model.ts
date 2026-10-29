export interface ChartPoint {
  label: string;
  value: number;
}

export interface AdminDashboard {
  kpis: {
    totalStudents: number;
    activeCourses: number;
    degreePrograms: number;
    activeEnrollments: number;
    waitlisted: number;
  } | null;
  enrollmentTrend: ChartPoint[] | null;
  studentsByProgram: ChartPoint[] | null;
  topCourses: { offeringId: string; code: string; name: string; lecturerName: string | null; enrolled: number }[] | null;
  recentRegistrations: {
    studentName: string | null;
    targetLabel: string | null;
    actorName: string;
    actorRole: string;
    action: string;
    createdAt: string;
  }[] | null;
  computedAt: string | null;
}

export interface LecturerCourse {
  offeringId: string;
  code: string;
  name: string;
  academicYearName: string;
  semesterName: string | null;
  enrolled: number;
  capacity: number;
  status: string;
  assignments: number;
  graded: number;
  submitted: number;
  classAverage: number | null;
}

export interface LecturerDashboard {
  myCourses: number;
  totalStudents: number;
  toGrade: number;
  averagePerformance: number | null;
  graded: number;
  pending: number;
  notSubmitted: number;
  courses: LecturerCourse[];
}

export interface StudentDashboard {
  programName: string | null;
  programCode: string | null;
  yearOfStudy: number | null;
  intakeYear: string | null;
  enrolledCourses: number;
  creditsCompleted: number;
  creditsRequired: number | null;
  gpa: number | null;
  upcomingDeadlines: number;
  gpaTrend: ChartPoint[];
  courses: {
    offeringId: string;
    code: string;
    name: string;
    lecturerName: string | null;
    requirementType: string | null;
    credits: number | null;
    percentage: number | null;
    letter: string | null;
  }[];
}

export interface ReportResponse {
  id: string;
  name: string;
  description: string;
  format: 'CSV' | 'PDF';
  lastGeneratedAt: string | null;
  available: boolean;
}

export interface ScheduleRequest {
  reportId: string;
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  time: string;
  dayOfWeek?: string | null;
  dayOfMonth?: number | null;
  recipients: string;
}

export interface ScheduleResponse {
  id: string;
  reportId: string;
  reportName: string;
  frequency: string;
  frequencyLabel: string;
  recipients: string;
  status: 'ACTIVE' | 'PAUSED';
  nextRunAt: string | null;
}
