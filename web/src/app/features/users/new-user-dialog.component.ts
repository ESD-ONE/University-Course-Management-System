import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../core/services/user.service';
import { CreatedUserResponse, RoleKind } from '../../core/models/user.model';

const ROLE_LABELS: Record<RoleKind, string> = {
  ADMIN: 'Administrator',
  LECTURER: 'Lecturer',
  STUDENT: 'Student',
};

/**
 * Role is locked to whichever tab the admin had open when they clicked "+ New" —
 * matches the role-based Users module (see docs/SYSTEM_GAP_ANALYSIS_AND_IMPLEMENTATION_PLAN.md
 * section D) rather than the wireframe's single modal with a role dropdown. Name, email,
 * status (Active/Inactive) — no password field. The backend generates a one-time temporary
 * password when none is supplied, and a Student ID automatically for Student role; both are
 * shown once on success, since there's no email/notification service yet to deliver them
 * any other way.
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
  @Output() closed = new EventEmitter<void>();
  @Output() created = new EventEmitter<CreatedUserResponse>();

  readonly roleLabels = ROLE_LABELS;

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

    const { name, email, status } = this.form.getRawValue();
    this.userService.create({ name: name!, email: email!, role: this.lockedRole, status: status! }).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.result.set(created);
        this.created.emit(created);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message ?? 'Could not create the user. Please try again.');
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
