export type RoleKind = 'ADMIN' | 'LECTURER' | 'STUDENT';
export type ScopeType = 'GLOBAL' | 'DEPARTMENT' | 'DEGREE_PROGRAM' | 'COURSE' | 'COURSE_MODULE';

export interface RolePermissionGrant {
  permissionId: string;
  module: string;
  actionKey: string;
  granted: boolean;
}

export interface RoleResponse {
  id: string;
  name: string;
  kind: RoleKind;
  permissions: RolePermissionGrant[];
}

export interface PermissionResponse {
  id: string;
  module: string;
  actionKey: string;
}

export interface AssignmentResponse {
  id: string;
  userId: string;
  roleId: string;
  roleName: string;
  scopeType: ScopeType;
  scopeId: string | null;
  overrides: RolePermissionGrant[];
}

/** One granted (module, action) at one scope, after overrides — what PermissionService
 * gates the UI on. Mirrors identity-access-service's EffectivePermission. */
export interface EffectivePermission {
  scopeType: ScopeType;
  scopeId: string | null;
  module: string;
  actionKey: string;
}

/** Who holds a role at a given scope — e.g. a degree program's Program Head. */
export interface ScopeHolderResponse {
  assignmentId: string;
  userId: string;
  userName: string;
  userEmail: string;
  roleId: string;
  roleName: string;
  scopeType: ScopeType;
  scopeId: string | null;
}
