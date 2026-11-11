import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ACADEMIC_API_BASE_URL, ENROLLMENT_API_BASE_URL } from '../config/api.config';
import { LearningTreeResponse, OfferingAccessResponse } from '../models/learning.model';
import { AuthService } from './auth.service';

/** The Learning Hub's "Academic Years" nav page: one tree, sourced from whichever
 * service owns the caller's view of it — academic-structure for staff (everything
 * they're scoped or assigned to teach), enrollment for students (their own courses). */
@Injectable({ providedIn: 'root' })
export class LearningService {
  constructor(
    private http: HttpClient,
    private auth: AuthService,
  ) {}

  tree(): Observable<LearningTreeResponse> {
    const isStudent = this.auth.currentUser()?.role === 'STUDENT';
    return isStudent
      ? this.http.get<LearningTreeResponse>(`${ENROLLMENT_API_BASE_URL}/learning/tree`)
      : this.http.get<LearningTreeResponse>(`${ACADEMIC_API_BASE_URL}/course-offerings/learning-tree`);
  }

  access(offeringId: string): Observable<OfferingAccessResponse> {
    return this.http.get<OfferingAccessResponse>(`${ACADEMIC_API_BASE_URL}/course-offerings/${offeringId}/access`);
  }
}
