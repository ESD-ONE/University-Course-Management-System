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
  status?: 'ACTIVE' | 'INACTIVE';
}

/** Mirrors Spring Data's Page<T> JSON shape, as returned by GET /users. */
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index, 0-based
  size: number;
}

export interface UserListQuery {
  role?: RoleKind;
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
