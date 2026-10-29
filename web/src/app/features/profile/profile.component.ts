import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { UserService } from '../../core/services/user.service';

/** Matches html-wireframes/index.html's profile(): name edit, change password, and
 *  notification preferences (owned by notification-service), all self-service. */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './profile.component.html',
})
export class ProfileComponent {
  private fb = inject(FormBuilder);
  private userService = inject(UserService);
  private notifications = inject(NotificationService);
  readonly auth = inject(AuthService);

  readonly nameForm = this.fb.group({
    name: [this.auth.currentUser()?.name ?? '', Validators.required],
  });

  readonly passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required],
  });

  readonly nameSaving = signal(false);
  readonly nameSaved = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordSaving = signal(false);
  readonly passwordSaved = signal(false);
  readonly prefsSaving = signal(false);
  readonly prefsSaved = signal(false);

  readonly prefs = signal({ email: true, assignments: true, grades: true, registrations: true });

  constructor() {
    // Preferences are owned by notification-service (it seeds them from the account's
    // older identity-access values the first time they're read).
    this.notifications.preferences().subscribe({
      next: (p) =>
        this.prefs.set({
          email: p.emailEnabled,
          assignments: p.assignmentAlerts,
          grades: p.gradeAlerts,
          registrations: p.registrationAlerts,
        }),
    });
  }

  togglePref(key: keyof ReturnType<typeof this.prefs>): void {
    this.prefs.update((p) => ({ ...p, [key]: !p[key] }));
  }

  initialsOf(name: string): string {
    return name
      .split(' ')
      .map((part) => part[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  saveName(): void {
    if (this.nameForm.invalid) return;
    this.nameSaving.set(true);
    this.nameSaved.set(false);
    this.userService.updateOwnName(this.nameForm.getRawValue().name!).subscribe({
      next: (user) => {
        this.auth.setCurrentUser(user);
        this.nameSaving.set(false);
        this.nameSaved.set(true);
        setTimeout(() => this.nameSaved.set(false), 2500);
      },
      error: () => this.nameSaving.set(false),
    });
  }

  savePassword(): void {
    this.passwordError.set(null);
    if (this.passwordForm.invalid) {
      this.passwordError.set('Fill in all three fields - the new password needs 12 to 128 characters.');
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.passwordError.set('New password and confirmation don’t match.');
      return;
    }
    this.passwordSaving.set(true);
    this.passwordSaved.set(false);
    this.userService.updateOwnPassword(currentPassword!, newPassword!).subscribe({
      next: () => {
        this.passwordSaving.set(false);
        this.passwordSaved.set(true);
        this.passwordForm.reset();
        setTimeout(() => this.passwordSaved.set(false), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.passwordSaving.set(false);
        this.passwordError.set(err.error?.message ?? 'Could not update your password. Please try again.');
      },
    });
  }

  savePrefs(): void {
    this.prefsSaving.set(true);
    this.prefsSaved.set(false);
    const p = this.prefs();
    this.notifications
      .updatePreferences({
        emailEnabled: p.email,
        assignmentAlerts: p.assignments,
        gradeAlerts: p.grades,
        registrationAlerts: p.registrations,
      })
      .subscribe({
      next: () => {
        this.prefsSaving.set(false);
        this.prefsSaved.set(true);
        setTimeout(() => this.prefsSaved.set(false), 2500);
      },
      error: () => this.prefsSaving.set(false),
    });
  }
}
