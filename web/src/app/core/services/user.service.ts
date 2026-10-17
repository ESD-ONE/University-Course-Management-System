import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  CreatedUserResponse,
  CreateUserRequest,
  PagedResponse,
  ProfileResponse,
  RoleKind,
  UpdateNotificationPreferencesRequest,
  UserListQuery,
  UserResponse,
  UserStatus,
  UserSummary,
} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  me(): Observable<ProfileResponse> {
    return this.http.get<ProfileResponse>(`${API_BASE_URL}/users/me`);
  }

  updateOwnName(name: string): Observable<ProfileResponse> {
    return this.http.patch<ProfileResponse>(`${API_BASE_URL}/users/me`, { name });
  }

  updateOwnPassword(currentPassword: string, newPassword: string): Observable<ProfileResponse> {
    return this.http.patch<ProfileResponse>(`${API_BASE_URL}/users/me/password`, { currentPassword, newPassword });
  }

  updateOwnNotificationPreferences(prefs: UpdateNotificationPreferencesRequest): Observable<ProfileResponse> {
    return this.http.patch<ProfileResponse>(`${API_BASE_URL}/users/me/notification-preferences`, prefs);
  }

  list(query: UserListQuery): Observable<PagedResponse<UserResponse>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.role) params = params.set('role', query.role);
    if (query.status) params = params.set('status', query.status);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<PagedResponse<UserResponse>>(`${API_BASE_URL}/users`, { params });
  }

  summary(): Observable<UserSummary> {
    return this.http.get<UserSummary>(`${API_BASE_URL}/users/summary`);
  }

  create(request: CreateUserRequest): Observable<CreatedUserResponse> {
    return this.http.post<CreatedUserResponse>(`${API_BASE_URL}/users`, request);
  }

  updateStatus(id: string, status: UserStatus): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${API_BASE_URL}/users/${id}/status`, { status });
  }

  updateRole(id: string, role: RoleKind): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${API_BASE_URL}/users/${id}/role`, { role });
  }
}
