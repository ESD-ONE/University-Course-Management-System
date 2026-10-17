import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';

/**
 * Matches html-wireframes/index.html's profile() (lines 2199-2234) exactly:
 * name edit, change password, and notification preferences, all self-service —
 * see docs/FULL_SYSTEM_GAP_ANALYSIS.md section D for why this view, specifically,
 * is fully buildable without a new backend service when the other 19 missing
 * views aren't.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './profile.component.html',
})
export class ProfileComponent {
  private fb = inject(FormBuilder);
  private userService = inject(UserService);
  readonly auth = inject(AuthService);

  readonly nameForm = this.fb.group({
    name: [this.auth.currentUser()?.name ?? '', Validators.required],
  });

  readonly passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
  });

  readonly nameSaving = signal(false);
  readonly nameSaved = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordSaving = signal(false);
  readonly passwordSaved = signal(false);
  readonly prefsSaving = signal(false);
  readonly prefsSaved = signal(false);

  readonly prefs = signal({
    email: this.auth.currentUser()?.notifyEmail ?? true,
    assignments: this.auth.currentUser()?.notifyAssignments ?? true,
    grades: this.auth.currentUser()?.notifyGrades ?? true,
    registrations: this.auth.currentUser()?.notifyRegistrations ?? true,
  });

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
      this.passwordError.set('Fill in all three fields — the new password needs at least 8 characters.');
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
    this.userService.updateOwnNotificationPreferences(this.prefs()).subscribe({
      next: (user) => {
        this.auth.setCurrentUser(user);
        this.prefsSaving.set(false);
        this.prefsSaved.set(true);
        setTimeout(() => this.prefsSaved.set(false), 2500);
      },
      error: () => this.prefsSaving.set(false),
    });
  }
}
