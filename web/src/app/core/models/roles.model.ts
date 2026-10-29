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
