import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AssignmentResponse, PermissionResponse, RoleKind, RoleResponse, ScopeType } from '../models/roles.model';

/** identity-access-service's roles, permission matrix, and scope assignments — the API
 *  that existed before this screen did. */
@Injectable({ providedIn: 'root' })
export class RolesService {
  constructor(private http: HttpClient) {}

  roles(): Observable<RoleResponse[]> {
    return this.http.get<RoleResponse[]>(`${API_BASE_URL}/roles`);
  }

  permissions(): Observable<PermissionResponse[]> {
    return this.http.get<PermissionResponse[]>(`${API_BASE_URL}/permissions`);
  }

  createRole(name: string, kind: RoleKind): Observable<RoleResponse> {
    return this.http.post<RoleResponse>(`${API_BASE_URL}/roles`, { name, kind });
  }

  setGrant(roleId: string, permissionId: string, granted: boolean): Observable<RoleResponse> {
    return this.http.patch<RoleResponse>(`${API_BASE_URL}/roles/${roleId}/permissions`, { permissionId, granted });
  }

  assignments(userId: string): Observable<AssignmentResponse[]> {
    return this.http.get<AssignmentResponse[]>(`${API_BASE_URL}/users/${userId}/assignments`);
  }

  assign(userId: string, roleId: string, scopeType: ScopeType, scopeId: string | null): Observable<AssignmentResponse> {
    return this.http.post<AssignmentResponse>(`${API_BASE_URL}/users/${userId}/assignments`, { roleId, scopeType, scopeId });
  }

  revoke(assignmentId: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/assignments/${assignmentId}`);
  }
}
