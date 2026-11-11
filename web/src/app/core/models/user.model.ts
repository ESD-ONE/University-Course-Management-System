/** Mirrors com.ucms.identity.domain.RoleKind exactly — the backend enum is fixed to these 3. */
export type RoleKind = 'ADMIN' | 'LECTURER' | 'STUDENT';

/** Mirrors com.ucms.identity.domain.UserStatus. */
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

/** Mirrors UserResponse — returned by GET /users, GET /users/me, and the update endpoints. */
export interface UserResponse {
  id: string;
  name: string;
  email: string;
  role: RoleKind;
  status: UserStatus;
  studentId: string | null;
  /** The user's account role's own id/name — distinct from any custom role (e.g.
   * "Program Head") they might separately hold through a scope assignment. */
  roleId: string;
  roleName: string;
}

/** Mirrors CreatedUserResponse — only POST /users returns this (never a later read). */
export interface CreatedUserResponse extends UserResponse {
  temporaryPassword: string | null;
}

/** Mirrors CreateUserRequest — password/status are optional, matching the backend's contract. */
export interface CreateUserRequest {
  name: string;
  email: string;
  password?: string;
  role: RoleKind;
  /** The specific role to create the account with — e.g. a custom role's own id.
   * Must share role's kind. Omitted falls back to that kind's default seeded role. */
  roleId?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}

/** Mirrors the scope of a `role_scope_assignment` row — how broad the assignment's authority is. */
export type AssignmentScopeType = 'GLOBAL' | 'DEPARTMENT' | 'DEGREE_PROGRAM' | 'COURSE' | 'COURSE_MODULE';

/** Mirrors AssignmentResponse as embedded on ProfileResponse — one role-scope grant held by this user. */
export interface AssignmentResponse {
  id: string;
  roleId: string;
  roleName: string;
  scopeType: AssignmentScopeType;
  scopeId: string | null;
}

/** Mirrors ProfileResponse — only GET /users/me returns this (own account, including prefs). */
export interface ProfileResponse extends UserResponse {
  notifyEmail: boolean;
  notifyAssignments: boolean;
  notifyGrades: boolean;
  notifyRegistrations: boolean;
  /** This user's role-scope grants (e.g. a DEPARTMENT-scoped "Department Head" assignment).
   * Added by identity-access-service alongside G-01's frontend authorization work. */
  assignments: AssignmentResponse[];
}

export interface UpdateNotificationPreferencesRequest {
  email: boolean;
  assignments: boolean;
  grades: boolean;
  registrations: boolean;
}

export interface UserListQuery {
  role?: RoleKind;
  /** Any role id, including a custom role like "Program Head" — matches the user's
   * account role OR a scope assignment of this role, whichever applies. */
  roleId?: string;
  status?: UserStatus;
  q?: string;
  page?: number;
  size?: number;
}

/** Mirrors UserSummaryResponse — server-computed counts, independent of any active filter. */
export interface UserSummary {
  total: number;
  active: number;
  students: number;
  staff: number;
}
