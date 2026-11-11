import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RoleResponse } from '../../core/models/roles.model';
import { UserResponse } from '../../core/models/user.model';
import { RolesService } from '../../core/services/roles.service';
import { UserService } from '../../core/services/user.service';

/** Every system role (seeded Admin/Lecturer/Student and any admin-created role, e.g.
 *  "Program Head") — not just the three account kinds, so a custom role can be set as a
 *  user's actual account role here. */
@Component({
  selector: 'app-change-role-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './change-role-dialog.component.html',
})
export class ChangeRoleDialogComponent implements OnInit {
  @Input({ required: true }) user!: UserResponse;
  @Output() closed = new EventEmitter<void>();
  @Output() changed = new EventEmitter<UserResponse>();

  readonly roles = signal<RoleResponse[]>([]);
  selectedRoleId = '';
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly loading = signal(true);

  constructor(private userService: UserService, private rolesService: RolesService) {}

  ngOnInit(): void {
    this.selectedRoleId = this.user.roleId;
    this.rolesService.roles().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load the list of roles.');
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.userService.updateRole(this.user.id, this.selectedRoleId).subscribe({
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
