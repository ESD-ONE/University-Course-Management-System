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
