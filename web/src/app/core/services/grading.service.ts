import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRADING_API_BASE_URL } from '../config/api.config';
import {
  AssignmentRequest,
  AssignmentResponse,
  AssignmentStatus,
  GradeHistoryResponse,
  GradeResponse,
  MySubmissionResponse,
  SubmissionRow,
} from '../models/grading.model';

@Injectable({ providedIn: 'root' })
export class GradingService {
  constructor(private http: HttpClient) {}

  list(offeringId?: string, status?: AssignmentStatus): Observable<AssignmentResponse[]> {
    let params = new HttpParams();
    if (offeringId) params = params.set('offeringId', offeringId);
    if (status) params = params.set('status', status);
    return this.http.get<AssignmentResponse[]>(`${GRADING_API_BASE_URL}/assignments`, { params });
  }

  get(id: string): Observable<AssignmentResponse> {
    return this.http.get<AssignmentResponse>(`${GRADING_API_BASE_URL}/assignments/${id}`);
  }

  create(request: AssignmentRequest): Observable<AssignmentResponse> {
    return this.http.post<AssignmentResponse>(`${GRADING_API_BASE_URL}/assignments`, request);
  }

  update(id: string, request: AssignmentRequest): Observable<AssignmentResponse> {
    return this.http.put<AssignmentResponse>(`${GRADING_API_BASE_URL}/assignments/${id}`, request);
  }

  setStatus(id: string, status: AssignmentStatus): Observable<AssignmentResponse> {
    return this.http.patch<AssignmentResponse>(`${GRADING_API_BASE_URL}/assignments/${id}/status`, { status });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${GRADING_API_BASE_URL}/assignments/${id}`);
  }

  uploadBrief(id: string, file: File): Observable<AssignmentResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AssignmentResponse>(`${GRADING_API_BASE_URL}/assignments/${id}/brief`, form);
  }

  downloadBrief(id: string): Observable<Blob> {
    return this.http.get(`${GRADING_API_BASE_URL}/assignments/${id}/brief`, { responseType: 'blob' });
  }

  submissions(assignmentId: string): Observable<SubmissionRow[]> {
    return this.http.get<SubmissionRow[]>(`${GRADING_API_BASE_URL}/assignments/${assignmentId}/submissions`);
  }

  submit(assignmentId: string, file: File): Observable<MySubmissionResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<MySubmissionResponse>(`${GRADING_API_BASE_URL}/assignments/${assignmentId}/submissions`, form);
  }

  mySubmission(assignmentId: string): Observable<MySubmissionResponse> {
    return this.http.get<MySubmissionResponse>(`${GRADING_API_BASE_URL}/assignments/${assignmentId}/submissions/me`);
  }

  downloadSubmission(submissionId: string): Observable<Blob> {
    return this.http.get(`${GRADING_API_BASE_URL}/submissions/${submissionId}/file`, { responseType: 'blob' });
  }

  grade(submissionId: string, marks: number, feedback: string | null): Observable<GradeResponse> {
    return this.http.put<GradeResponse>(`${GRADING_API_BASE_URL}/submissions/${submissionId}/grade`, { marks, feedback });
  }

  gradeAll(
    assignmentId: string,
    grades: { submissionId: string; marks: number; feedback: string | null }[],
  ): Observable<GradeResponse[]> {
    return this.http.put<GradeResponse[]>(`${GRADING_API_BASE_URL}/assignments/${assignmentId}/grades`, { grades });
  }

  myGrades(): Observable<GradeHistoryResponse> {
    return this.http.get<GradeHistoryResponse>(`${GRADING_API_BASE_URL}/grades/me`);
  }
}
