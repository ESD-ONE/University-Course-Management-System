import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ANALYTICS_API_BASE_URL } from '../config/api.config';
import {
  AdminDashboard,
  LecturerDashboard,
  ReportResponse,
  ScheduleRequest,
  ScheduleResponse,
  StudentDashboard,
} from '../models/analytics.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  constructor(private http: HttpClient) {}

  adminDashboard(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${ANALYTICS_API_BASE_URL}/dashboards/admin`);
  }

  refreshAdminDashboard(): Observable<AdminDashboard> {
    return this.http.post<AdminDashboard>(`${ANALYTICS_API_BASE_URL}/dashboards/admin/refresh`, {});
  }

  lecturerDashboard(): Observable<LecturerDashboard> {
    return this.http.get<LecturerDashboard>(`${ANALYTICS_API_BASE_URL}/dashboards/lecturer`);
  }

  studentDashboard(): Observable<StudentDashboard> {
    return this.http.get<StudentDashboard>(`${ANALYTICS_API_BASE_URL}/dashboards/student`);
  }

  reports(): Observable<ReportResponse[]> {
    return this.http.get<ReportResponse[]>(`${ANALYTICS_API_BASE_URL}/reports`);
  }

  generate(reportId: string): Observable<ReportResponse> {
    return this.http.post<ReportResponse>(`${ANALYTICS_API_BASE_URL}/reports/${reportId}/generate`, {});
  }

  download(reportId: string): Observable<Blob> {
    return this.http.get(`${ANALYTICS_API_BASE_URL}/reports/${reportId}/download`, { responseType: 'blob' });
  }

  schedules(): Observable<ScheduleResponse[]> {
    return this.http.get<ScheduleResponse[]>(`${ANALYTICS_API_BASE_URL}/scheduled-reports`);
  }

  createSchedule(request: ScheduleRequest): Observable<ScheduleResponse> {
    return this.http.post<ScheduleResponse>(`${ANALYTICS_API_BASE_URL}/scheduled-reports`, request);
  }

  setScheduleStatus(id: string, status: 'ACTIVE' | 'PAUSED'): Observable<ScheduleResponse> {
    return this.http.patch<ScheduleResponse>(`${ANALYTICS_API_BASE_URL}/scheduled-reports/${id}/status`, { status });
  }

  deleteSchedule(id: string): Observable<void> {
    return this.http.delete<void>(`${ANALYTICS_API_BASE_URL}/scheduled-reports/${id}`);
  }
}
