import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { RolesPermissionsComponent } from './roles-permissions.component';
import { RolesService } from '../../core/services/roles.service';
import { UserService } from '../../core/services/user.service';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { AssignmentResponse, PermissionResponse, RoleResponse } from '../../core/models/roles.model';
import { UserResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';
import { DepartmentResponse } from '../../core/models/reference-data.model';

describe('RolesPermissionsComponent', () => {
  let rolesService: jasmine.SpyObj<RolesService>;
  let userService: jasmine.SpyObj<UserService>;
  let referenceData: jasmine.SpyObj<ReferenceDataService>;

  const permissions = [
    { id: 'p1', module: 'Users', actionKey: 'View' },
    { id: 'p2', module: 'Users', actionKey: 'Create' },
    { id: 'p3', module: 'Courses', actionKey: 'View' },
  ] as PermissionResponse[];

  const roleAdmin = {
    id: 'r1',
    name: 'Administrator',
    kind: 'ADMIN',
    permissions: [
      { permissionId: 'p1', granted: true },
      { permissionId: 'p2', granted: false },
    ],
  } as RoleResponse;

  function make(): RolesPermissionsComponent {
    return TestBed.createComponent(RolesPermissionsComponent).componentInstance;
  }

  beforeEach(() => {
    rolesService = jasmine.createSpyObj<RolesService>('RolesService', [
      'roles', 'permissions', 'setGrant', 'createRole', 'assignments', 'assign', 'revoke',
    ]);
    userService = jasmine.createSpyObj<UserService>('UserService', ['list']);
    referenceData = jasmine.createSpyObj<ReferenceDataService>('ReferenceDataService', ['listDepartments']);

    rolesService.roles.and.returnValue(of([roleAdmin]));
    rolesService.permissions.and.returnValue(of(permissions));
    rolesService.assignments.and.returnValue(of([]));
    referenceData.listDepartments.and.returnValue(
      of({ content: [{ id: 'd1', name: 'CS' }] } as unknown as PagedResponse<DepartmentResponse>),
    );
    userService.list.and.returnValue(of({ content: [{ id: 'u1', name: 'Ada' }] } as unknown as PagedResponse<UserResponse>));

    TestBed.configureTestingModule({
      providers: [
        { provide: RolesService, useValue: rolesService },
        { provide: UserService, useValue: userService },
        { provide: ReferenceDataService, useValue: referenceData },
      ],
    });
  });

  it('loads roles + permissions and defaults the selected/assign role to the first', () => {
    const c = make();
    expect(c.roles().length).toBe(1);
    expect(c.permissions().length).toBe(3);
    expect(c.selectedRoleId()).toBe('r1');
    expect(c.assignRoleId()).toBe('r1');
    expect(c.loading()).toBeFalse();
  });

  it('derives the action columns in canonical order and the module rows', () => {
    const c = make();
    expect(c.actions()).toEqual(['View', 'Create']); // Edit/Delete/manage absent
    expect(c.modules()).toEqual(['Users', 'Courses']);
  });

  it('granted / grantedCount read the selected role’s grants', () => {
    const c = make();
    expect(c.granted('p1')).toBeTrue();
    expect(c.granted('p2')).toBeFalse();
    expect(c.grantedCount(roleAdmin)).toBe(1);
  });

  it('toggle flips a grant via the service and stores the updated role', () => {
    const updated = { ...roleAdmin, permissions: [{ permissionId: 'p2', granted: true }] } as RoleResponse;
    rolesService.setGrant.and.returnValue(of(updated));
    const c = make();
    c.toggle(permissions[1]); // p2, currently false -> true
    expect(rolesService.setGrant).toHaveBeenCalledWith('r1', 'p2', true);
    expect(c.roles()[0].permissions).toEqual(updated.permissions);
  });

  it('moduleLabel marks legacy lower-case modules', () => {
    const c = make();
    expect(c.moduleLabel('Users')).toBe('Users');
    expect(c.moduleLabel('users')).toBe('Users (legacy, all-or-nothing)');
  });

  it('createRole appends the new role and selects it', () => {
    const newRole = { id: 'r2', name: 'Program Head', kind: 'LECTURER', permissions: [] } as RoleResponse;
    rolesService.createRole.and.returnValue(of(newRole));
    const c = make();
    c.newRoleName.set('Program Head');
    c.newRoleKind.set('LECTURER');
    c.createRole();
    expect(rolesService.createRole).toHaveBeenCalledWith('Program Head', 'LECTURER');
    expect(c.roles().map((r) => r.id)).toContain('r2');
    expect(c.selectedRoleId()).toBe('r2');
  });

  it('selectUser loads that user’s assignments', () => {
    const c = make();
    c.selectUser({ id: 'u1', name: 'Ada' } as UserResponse);
    expect(c.selectedUser()?.id).toBe('u1');
    expect(rolesService.assignments).toHaveBeenCalledWith('u1');
  });

  it('assign refuses a DEPARTMENT scope with no department chosen', () => {
    const c = make();
    c.selectUser({ id: 'u1', name: 'Ada' } as UserResponse);
    c.assignScope.set('DEPARTMENT');
    c.assignDepartmentId.set('');
    c.assign();
    expect(rolesService.assign).not.toHaveBeenCalled();
    expect(c.toast()?.error).toBeTrue();
  });

  it('assign posts a department-scoped assignment when a department is chosen', () => {
    rolesService.assign.and.returnValue(of({ id: 'a1' } as unknown as AssignmentResponse));
    const c = make();
    c.selectUser({ id: 'u1', name: 'Ada' } as UserResponse);
    c.assignScope.set('DEPARTMENT');
    c.assignDepartmentId.set('d1');
    c.assign();
    expect(rolesService.assign).toHaveBeenCalledWith('u1', 'r1', 'DEPARTMENT', 'd1');
  });

  it('searchUsers debounces and populates the user list', fakeAsync(() => {
    const c = make();
    userService.list.calls.reset();
    c.searchUsers('ad');
    tick(300);
    expect(userService.list).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'ad', size: 10 }));
    expect(c.users().length).toBe(1);
  }));
});
