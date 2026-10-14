import { RoleKind } from './user.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Mirrors LoginResponse. */
export interface LoginResponse {
  token: string;
  role: RoleKind;
  name: string;
}
