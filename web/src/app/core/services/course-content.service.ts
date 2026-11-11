import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRADING_API_BASE_URL } from '../config/api.config';
import {
  ContentItemStatus,
  ContentItemType,
  CreateLinkItemRequest,
  CreateSessionRequest,
  CreateWeekRequest,
  ItemResponse,
  SessionResponse,
  UpdateItemRequest,
  UpdateSessionRequest,
  UpdateWeekRequest,
  WeekResponse,
} from '../models/course-content.model';

/** One course offering's week-by-week delivery: weeks, their lecture sessions, and
 * their materials. Assignments are fetched separately (GradingService) and merged by
 * weekId in the course workspace component — see
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7. */
@Injectable({ providedIn: 'root' })
export class CourseContentService {
  constructor(private http: HttpClient) {}

  listWeeks(offeringId: string): Observable<WeekResponse[]> {
    return this.http.get<WeekResponse[]>(`${GRADING_API_BASE_URL}/offerings/${offeringId}/weeks`);
  }

  createWeek(offeringId: string, request: CreateWeekRequest): Observable<WeekResponse> {
    return this.http.post<WeekResponse>(`${GRADING_API_BASE_URL}/offerings/${offeringId}/weeks`, request);
  }

  updateWeek(weekId: string, request: UpdateWeekRequest): Observable<WeekResponse> {
    return this.http.put<WeekResponse>(`${GRADING_API_BASE_URL}/weeks/${weekId}`, request);
  }

  deleteWeek(weekId: string): Observable<void> {
    return this.http.delete<void>(`${GRADING_API_BASE_URL}/weeks/${weekId}`);
  }

  createSession(weekId: string, request: CreateSessionRequest): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${GRADING_API_BASE_URL}/weeks/${weekId}/sessions`, request);
  }

  updateSession(sessionId: string, request: UpdateSessionRequest): Observable<SessionResponse> {
    return this.http.put<SessionResponse>(`${GRADING_API_BASE_URL}/sessions/${sessionId}`, request);
  }

  deleteSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${GRADING_API_BASE_URL}/sessions/${sessionId}`);
  }

  createLinkItem(request: CreateLinkItemRequest): Observable<ItemResponse> {
    return this.http.post<ItemResponse>(`${GRADING_API_BASE_URL}/content-items/link`, request);
  }

  uploadFileItem(
    weekId: string,
    sessionId: string | null,
    type: ContentItemType,
    title: string,
    description: string | null,
    file: File,
  ): Observable<ItemResponse> {
    const form = new FormData();
    form.append('weekId', weekId);
    if (sessionId) form.append('sessionId', sessionId);
    form.append('type', type);
    form.append('title', title);
    if (description) form.append('description', description);
    form.append('file', file);
    return this.http.post<ItemResponse>(`${GRADING_API_BASE_URL}/content-items/file`, form);
  }

  updateItem(itemId: string, request: UpdateItemRequest): Observable<ItemResponse> {
    return this.http.put<ItemResponse>(`${GRADING_API_BASE_URL}/content-items/${itemId}`, request);
  }

  setItemStatus(itemId: string, status: ContentItemStatus): Observable<ItemResponse> {
    return this.http.patch<ItemResponse>(`${GRADING_API_BASE_URL}/content-items/${itemId}/status`, { status });
  }

  deleteItem(itemId: string): Observable<void> {
    return this.http.delete<void>(`${GRADING_API_BASE_URL}/content-items/${itemId}`);
  }

  downloadItem(itemId: string): Observable<Blob> {
    return this.http.get(`${GRADING_API_BASE_URL}/content-items/${itemId}/file`, { responseType: 'blob' });
  }
}
