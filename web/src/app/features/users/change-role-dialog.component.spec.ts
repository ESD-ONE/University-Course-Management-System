import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ChangeRoleDialogComponent } from './change-role-dialog.component';
import { UserService } from '../../core/services/user.service';
import { RolesService } from '../../core/services/roles.service';
import { RoleResponse } from '../../core/models/roles.model';
import { UserResponse } from '../../core/models/user.model';

describe('ChangeRoleDialogComponent', () => {
  let userService: jasmine.SpyObj<UserService>;
  let rolesService: jasmine.SpyObj<RolesService>;

  const user = { id: 'u1', name: 'Ada', roleId: 'role-student', roleName: 'Student' } as UserResponse;
  const roles = [
    { id: 'role-student', name: 'Student', kind: 'STUDENT' },
    { id: 'role-lect', name: 'Lecturer', kind: 'LECTURER' },
  ] as RoleResponse[];

  function make(): ChangeRoleDialogComponent {
    const c = TestBed.createComponent(ChangeRoleDialogComponent).componentInstance;
    c.user = user;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    userService = jasmine.createSpyObj<UserService>('UserService', ['updateRole']);
    rolesService = jasmine.createSpyObj<RolesService>('RolesService', ['roles']);
    rolesService.roles.and.returnValue(of(roles));
    TestBed.configureTestingModule({
      imports: [ChangeRoleDialogComponent],
      providers: [
        { provide: UserService, useValue: userService },
        { provide: RolesService, useValue: rolesService },
      ],
    });
  });

  it('loads roles and preselects the user’s current role', () => {
    const c = make();
    expect(c.roles().length).toBe(2);
    expect(c.selectedRoleId).toBe('role-student');
    expect(c.loading()).toBeFalse();
  });

  it('shows an error when roles cannot be loaded', () => {
    rolesService.roles.and.returnValue(throwError(() => new Error('down')));
    const c = make();
    expect(c.errorMessage()).toContain('Could not load');
    expect(c.loading()).toBeFalse();
  });

  it('save updates the role and emits the updated user', () => {
    const updated = { ...user, roleId: 'role-lect', roleName: 'Lecturer' } as UserResponse;
    userService.updateRole.and.returnValue(of(updated));
    const c = make();
    c.selectedRoleId = 'role-lect';
    const emitted: UserResponse[] = [];
    c.changed.subscribe((v) => emitted.push(v));

    c.save();

    expect(userService.updateRole).toHaveBeenCalledWith('u1', 'role-lect');
    expect(emitted).toEqual([updated]);
  });

  it('save surfaces a server error', () => {
    userService.updateRole.and.returnValue(
      throwError(() => new HttpErrorResponse({ error: { message: 'Not allowed' }, status: 403 })),
    );
    const c = make();
    c.save();
    expect(c.errorMessage()).toBe('Not allowed');
    expect(c.submitting()).toBeFalse();
  });

  it('close emits closed', () => {
    const c = make();
    let closed = false;
    c.closed.subscribe(() => (closed = true));
    c.close();
    expect(closed).toBeTrue();
  });
});
