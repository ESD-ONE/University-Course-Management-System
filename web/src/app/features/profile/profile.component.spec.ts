import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ProfileComponent } from './profile.component';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { NotificationService } from '../../core/services/notification.service';
import { ProfileResponse } from '../../core/models/user.model';
import { NotificationPreferences } from '../../core/models/notification.model';

describe('ProfileComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let userService: jasmine.SpyObj<UserService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  const user = { id: 'u1', name: 'Ada Lovelace' } as ProfileResponse;
  const serverPrefs: NotificationPreferences = {
    emailEnabled: false,
    assignmentAlerts: true,
    gradeAlerts: false,
    registrationAlerts: true,
  };

  function make(): ProfileComponent {
    return TestBed.createComponent(ProfileComponent).componentInstance;
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser', 'setCurrentUser']);
    auth.currentUser.and.returnValue(user);
    userService = jasmine.createSpyObj<UserService>('UserService', ['updateOwnName', 'updateOwnPassword']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['preferences', 'updatePreferences']);
    notifications.preferences.and.returnValue(of(serverPrefs));
    notifications.updatePreferences.and.returnValue(of(serverPrefs));

    TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: UserService, useValue: userService },
        { provide: NotificationService, useValue: notifications },
      ],
    });
  });

  it('loads notification preferences into the toggle state on construction', () => {
    const c = make();
    expect(c.prefs()).toEqual({ email: false, assignments: true, grades: false, registrations: true });
  });

  it('togglePref flips a single key', () => {
    const c = make();
    c.togglePref('grades');
    expect(c.prefs().grades).toBeTrue();
    expect(c.prefs().email).toBeFalse();
  });

  it('seeds the name form from the current user', () => {
    const c = make();
    expect(c.nameForm.getRawValue().name).toBe('Ada Lovelace');
  });

  it('saveName persists the new name and pushes it back into AuthService', () => {
    userService.updateOwnName.and.returnValue(of({ ...user, name: 'Ada L' }));
    const c = make();
    c.nameForm.setValue({ name: 'Ada L' });
    c.saveName();
    expect(userService.updateOwnName).toHaveBeenCalledWith('Ada L');
    expect(auth.setCurrentUser).toHaveBeenCalled();
    expect(c.nameSaved()).toBeTrue();
  });

  it('saveName does nothing when the name is blank', () => {
    const c = make();
    c.nameForm.setValue({ name: '' });
    c.saveName();
    expect(userService.updateOwnName).not.toHaveBeenCalled();
  });

  it('savePassword rejects a mismatched confirmation', () => {
    const c = make();
    c.passwordForm.setValue({ currentPassword: 'old', newPassword: 'abcdefghijkl', confirmPassword: 'different0000' });
    c.savePassword();
    expect(c.passwordError()).toContain('don’t match');
    expect(userService.updateOwnPassword).not.toHaveBeenCalled();
  });

  it('savePassword rejects an incomplete / too-short form', () => {
    const c = make();
    c.passwordForm.setValue({ currentPassword: 'old', newPassword: 'short', confirmPassword: 'short' });
    c.savePassword();
    expect(c.passwordError()).toContain('12 to 128');
    expect(userService.updateOwnPassword).not.toHaveBeenCalled();
  });

  it('savePassword submits a valid change and reports success', () => {
    userService.updateOwnPassword.and.returnValue(of(user));
    const c = make();
    c.passwordForm.setValue({ currentPassword: 'old', newPassword: 'abcdefghijkl', confirmPassword: 'abcdefghijkl' });
    c.savePassword();
    expect(userService.updateOwnPassword).toHaveBeenCalledWith('old', 'abcdefghijkl');
    expect(c.passwordSaved()).toBeTrue();
  });

  it('savePassword surfaces a server error message', () => {
    userService.updateOwnPassword.and.returnValue(
      throwError(() => new HttpErrorResponse({ error: { message: 'Current password is wrong' }, status: 400 })),
    );
    const c = make();
    c.passwordForm.setValue({ currentPassword: 'bad', newPassword: 'abcdefghijkl', confirmPassword: 'abcdefghijkl' });
    c.savePassword();
    expect(c.passwordError()).toBe('Current password is wrong');
  });

  it('savePrefs maps the toggles back to the server shape', () => {
    const c = make();
    c.savePrefs();
    expect(notifications.updatePreferences).toHaveBeenCalledWith({
      emailEnabled: false,
      assignmentAlerts: true,
      gradeAlerts: false,
      registrationAlerts: true,
    });
    expect(c.prefsSaved()).toBeTrue();
  });
});
