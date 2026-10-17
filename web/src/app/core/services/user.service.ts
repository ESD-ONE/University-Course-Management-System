import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CreatedUserResponse, CreateUserRequest, RoleKind, UserResponse, UserStatus } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${API_BASE_URL}/users/me`);
  }

  list(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${API_BASE_URL}/users`);
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
