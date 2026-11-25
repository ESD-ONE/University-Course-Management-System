import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { NewUserDialogComponent } from './new-user-dialog.component';
import { UserService } from '../../core/services/user.service';
import { CreatedUserResponse } from '../../core/models/user.model';

describe('NewUserDialogComponent', () => {
  let userService: jasmine.SpyObj<UserService>;

  function make(): NewUserDialogComponent {
    const c = TestBed.createComponent(NewUserDialogComponent).componentInstance;
    c.lockedRole = 'LECTURER';
    c.lockedRoleId = 'role-lect';
    c.lockedRoleName = 'Lecturer';
    return c;
  }

  beforeEach(() => {
    userService = jasmine.createSpyObj<UserService>('UserService', ['create']);
    TestBed.configureTestingModule({
      imports: [NewUserDialogComponent],
      providers: [{ provide: UserService, useValue: userService }],
    });
  });

  it('refuses to submit an invalid form and shows a message', () => {
    const c = make();
    c.form.setValue({ name: '', email: 'not-an-email', status: 'ACTIVE' });
    c.submit();
    expect(c.errorMessage()).toContain('full name and a valid email');
    expect(userService.create).not.toHaveBeenCalled();
  });

  it('creates the account with the locked role and emits the result', () => {
    const created = { id: 'u9', name: 'Grace', role: 'LECTURER', temporaryPassword: 'temp' } as CreatedUserResponse;
    userService.create.and.returnValue(of(created));
    const c = make();
    const emitted: CreatedUserResponse[] = [];
    c.created.subscribe((v) => emitted.push(v));

    c.form.setValue({ name: 'Grace', email: 'grace@ucms.local', status: 'ACTIVE' });
    c.submit();

    expect(userService.create).toHaveBeenCalledWith({
      name: 'Grace',
      email: 'grace@ucms.local',
      role: 'LECTURER',
      roleId: 'role-lect',
      status: 'ACTIVE',
    });
    expect(c.result()).toBe(created);
    expect(emitted).toEqual([created]);
  });

  it('surfaces a server error and re-enables the form', () => {
    userService.create.and.returnValue(
      throwError(() => new HttpErrorResponse({ error: { message: 'Email already in use' }, status: 409 })),
    );
    const c = make();
    c.form.setValue({ name: 'Grace', email: 'grace@ucms.local', status: 'ACTIVE' });
    c.submit();
    expect(c.errorMessage()).toBe('Email already in use');
    expect(c.submitting()).toBeFalse();
    expect(c.form.enabled).toBeTrue();
  });

  it('close emits closed', () => {
    const c = make();
    let closed = false;
    c.closed.subscribe(() => (closed = true));
    c.close();
    expect(closed).toBeTrue();
  });
});
