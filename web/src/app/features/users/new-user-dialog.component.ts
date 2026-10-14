import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../core/services/user.service';
import { CreatedUserResponse } from '../../core/models/user.model';

/**
 * Matches the designed "New User" modal exactly: name, email, role, status (Active/
 * Inactive only) — no password field. The backend generates a one-time temporary
 * password when none is supplied; this dialog shows it once on success, since there's
 * no email/notification service yet to deliver it any other way.
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

  @Output() closed = new EventEmitter<void>();
  @Output() created = new EventEmitter<CreatedUserResponse>();

  readonly form = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    role: ['STUDENT' as const, Validators.required],
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

    const { name, email, role, status } = this.form.getRawValue();
    this.userService.create({ name: name!, email: email!, role: role!, status: status! }).subscribe({
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
