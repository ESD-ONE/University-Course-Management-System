import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../core/services/user.service';
import { CreatedUserResponse, RoleKind } from '../../core/models/user.model';

/**
 * Role is locked to whichever tab the admin had open when they clicked "+ New" — the
 * Users screen has one tab per active role (not just the three account kinds), so this
 * creates the account already holding that exact role. Name, email, status (Active/
 * Inactive) — no password field. The backend generates a one-time temporary password
 * when none is supplied, and a Student ID automatically for Student role; both are shown
 * once on success, since there's no email/notification service yet to deliver them any
 * other way.
 */
@Component({
  selector: 'app-new-user-dialog',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './new-user-dialog.component.html',
})
export class NewUserDialogComponent {
  private fb = inject(FormBuilder);
  private userService = inject(UserService);

  @Input({ required: true }) lockedRole!: RoleKind;
  @Input({ required: true }) lockedRoleId!: string;
  @Input({ required: true }) lockedRoleName!: string;
  @Output() closed = new EventEmitter<void>();
  @Output() created = new EventEmitter<CreatedUserResponse>();

  readonly form = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    status: ['ACTIVE' as const, Validators.required],
  });

  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly result = signal<CreatedUserResponse | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.errorMessage.set('Enter the user’s full name and a valid email address.');
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.form.disable();

    const { name, email, status } = this.form.getRawValue();
    this.userService.create({ name: name!, email: email!, role: this.lockedRole, roleId: this.lockedRoleId, status: status! }).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.form.enable();
        this.result.set(created);
        this.created.emit(created);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.form.enable();
        this.errorMessage.set(err.error?.message ?? 'Could not create the user. Please try again.');
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
