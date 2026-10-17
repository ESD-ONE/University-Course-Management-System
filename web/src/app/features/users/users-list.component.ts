import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { UserResponse, UserStatus } from '../../core/models/user.model';
import { UserService } from '../../core/services/user.service';
import { ChangeRoleDialogComponent } from './change-role-dialog.component';
import { NewUserDialogComponent } from './new-user-dialog.component';

@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [NewUserDialogComponent, ChangeRoleDialogComponent],
  templateUrl: './users-list.component.html',
})
export class UsersListComponent implements OnInit {
  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly showNewUserDialog = signal(false);
  readonly roleDialogTarget = signal<UserResponse | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load users.');
        this.loading.set(false);
      },
    });
  }

  onUserCreated(): void {
    this.load();
    this.showToast('User created.');
  }

  onRoleChanged(updated: UserResponse): void {
    this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
    this.roleDialogTarget.set(null);
    this.showToast(`${updated.name}'s role is now ${updated.role}.`);
  }

  cycleStatus(user: UserResponse): void {
    // Matches the wireframe's single toggle button, extended to cover all 3 real
    // statuses: Active -> Inactive -> Suspended -> back to Active.
    const next: Record<UserStatus, UserStatus> = {
      ACTIVE: 'INACTIVE',
      INACTIVE: 'SUSPENDED',
      SUSPENDED: 'ACTIVE',
    };
    const newStatus = next[user.status];
    this.userService.updateStatus(user.id, newStatus).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.showToast(`${updated.name} is now ${updated.status.toLowerCase()}.`);
      },
      error: (err: HttpErrorResponse) => {
        this.showToast(err.error?.message ?? 'Could not update status.', true);
      },
    });
  }

  private showToast(message: string, error = false): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
