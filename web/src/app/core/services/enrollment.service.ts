import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ENROLLMENT_API_BASE_URL } from '../config/api.config';
import {
  CatalogEntry,
  CourseEnrollmentResponse,
  CreateProgramEnrollmentRequest,
  EnrollRequest,
  MyStudentResponse,
  ProgramEnrollmentResponse,
  ProgramEnrollmentStatus,
  RegistrationResponse,
  SeatCount,
  StudentOption,
} from '../models/enrollment.model';

@Injectable({ providedIn: 'root' })
export class EnrollmentService {
  constructor(private http: HttpClient) {}

  catalog(academicYearId?: string): Observable<CatalogEntry[]> {
    const params = academicYearId ? new HttpParams().set('academicYearId', academicYearId) : undefined;
    return this.http.get<CatalogEntry[]>(`${ENROLLMENT_API_BASE_URL}/catalog`, { params });
  }

  enroll(request: EnrollRequest): Observable<CourseEnrollmentResponse> {
    return this.http.post<CourseEnrollmentResponse>(`${ENROLLMENT_API_BASE_URL}/course-enrollments`, request);
  }

  drop(enrollmentId: string): Observable<void> {
    return this.http.delete<void>(`${ENROLLMENT_API_BASE_URL}/course-enrollments/${enrollmentId}`);
  }

  myEnrollments(): Observable<CourseEnrollmentResponse[]> {
    return this.http.get<CourseEnrollmentResponse[]>(`${ENROLLMENT_API_BASE_URL}/course-enrollments/me`);
  }

  roster(offeringId: string): Observable<CourseEnrollmentResponse[]> {
    return this.http.get<CourseEnrollmentResponse[]>(`${ENROLLMENT_API_BASE_URL}/course-enrollments`, {
      params: new HttpParams().set('offeringId', offeringId),
    });
  }

  myStudents(): Observable<MyStudentResponse[]> {
    return this.http.get<MyStudentResponse[]>(`${ENROLLMENT_API_BASE_URL}/course-enrollments/my-students`);
  }

  seats(offeringIds: string[]): Observable<SeatCount[]> {
    let params = new HttpParams();
    offeringIds.forEach((id) => (params = params.append('ids', id)));
    return this.http.get<SeatCount[]>(`${ENROLLMENT_API_BASE_URL}/offerings/seats`, { params });
  }

  registerProgram(request: CreateProgramEnrollmentRequest): Observable<ProgramEnrollmentResponse> {
    return this.http.post<ProgramEnrollmentResponse>(`${ENROLLMENT_API_BASE_URL}/program-enrollments`, request);
  }

  programEnrollments(studentId?: string): Observable<ProgramEnrollmentResponse[]> {
    const params = studentId ? new HttpParams().set('studentId', studentId) : undefined;
    return this.http.get<ProgramEnrollmentResponse[]>(`${ENROLLMENT_API_BASE_URL}/program-enrollments`, { params });
  }

  myProgramEnrollments(): Observable<ProgramEnrollmentResponse[]> {
    return this.http.get<ProgramEnrollmentResponse[]>(`${ENROLLMENT_API_BASE_URL}/program-enrollments/me`);
  }

  updateProgramEnrollment(
    id: string,
    body: { currentYearOfStudy?: number; status?: ProgramEnrollmentStatus },
  ): Observable<ProgramEnrollmentResponse> {
    return this.http.patch<ProgramEnrollmentResponse>(`${ENROLLMENT_API_BASE_URL}/program-enrollments/${id}`, body);
  }

  searchStudents(q: string): Observable<StudentOption[]> {
    return this.http.get<StudentOption[]>(`${ENROLLMENT_API_BASE_URL}/students`, { params: new HttpParams().set('q', q) });
  }

  registrations(limit = 20): Observable<RegistrationResponse[]> {
    return this.http.get<RegistrationResponse[]>(`${ENROLLMENT_API_BASE_URL}/registrations`, {
      params: new HttpParams().set('limit', limit),
    });
  }
}
