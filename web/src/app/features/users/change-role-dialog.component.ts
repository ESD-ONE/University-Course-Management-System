import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RoleKind, UserResponse } from '../../core/models/user.model';
import { UserService } from '../../core/services/user.service';

@Component({
  selector: 'app-change-role-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './change-role-dialog.component.html',
})
export class ChangeRoleDialogComponent implements OnInit {
  @Input({ required: true }) user!: UserResponse;
  @Output() closed = new EventEmitter<void>();
  @Output() changed = new EventEmitter<UserResponse>();

  selectedRole: RoleKind = this.user?.role ?? 'STUDENT';
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.selectedRole = this.user.role;
  }

  save(): void {
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.userService.updateRole(this.user.id, this.selectedRole).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.changed.emit(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message ?? 'Could not change the role. Please try again.');
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
