import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreatedUserResponse, RoleKind, UserResponse, UserStatus, UserSummary } from '../../core/models/user.model';
import { UserService } from '../../core/services/user.service';
import { ChangeRoleDialogComponent } from './change-role-dialog.component';
import { NewUserDialogComponent } from './new-user-dialog.component';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface RoleTab {
  role: RoleKind;
  label: string;
  addLabel: string;
}

const TABS: RoleTab[] = [
  { role: 'ADMIN', label: 'Administrators', addLabel: '+ New Administrator' },
  { role: 'LECTURER', label: 'Lecturers', addLabel: '+ New Lecturer' },
  { role: 'STUDENT', label: 'Students', addLabel: '+ New Student' },
];

/** Independent filter/pagination state per tab, so switching tabs doesn't lose the others' filters. */
interface TabState {
  status: UserStatus | '';
  q: string;
  page: number;
  users: UserResponse[];
  totalElements: number;
  totalPages: number;
  loading: boolean;
  error: string | null;
}

function freshTabState(): TabState {
  return { status: '', q: '', page: 0, users: [], totalElements: 0, totalPages: 0, loading: true, error: null };
}

@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [FormsModule, NewUserDialogComponent, ChangeRoleDialogComponent],
  templateUrl: './users-list.component.html',
})
export class UsersListComponent implements OnInit {
  readonly tabs = TABS;
  readonly activeTab = signal<RoleTab>(TABS[0]);

  readonly tabState = signal<Record<RoleKind, TabState>>({
    ADMIN: freshTabState(),
    LECTURER: freshTabState(),
    STUDENT: freshTabState(),
  });

  readonly summary = signal<UserSummary | null>(null);
  readonly showNewUserDialog = signal(false);
  readonly roleDialogTarget = signal<UserResponse | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  private searchDebounce: ReturnType<typeof setTimeout> | undefined;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    for (const tab of this.tabs) {
      this.load(tab.role);
    }
    this.loadSummary();
  }

  selectTab(tab: RoleTab): void {
    this.activeTab.set(tab);
  }

  current(): TabState {
    return this.tabState()[this.activeTab().role];
  }

  pageRangeStart(): number {
    return this.current().page * PAGE_SIZE + 1;
  }

  pageRangeEnd(): number {
    return Math.min((this.current().page + 1) * PAGE_SIZE, this.current().totalElements);
  }

  onSearchInput(value: string): void {
    const role = this.activeTab().role;
    this.patchTab(role, { q: value, page: 0 });
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(role), SEARCH_DEBOUNCE_MS);
  }

  onStatusFilterChange(value: UserStatus | ''): void {
    const role = this.activeTab().role;
    this.patchTab(role, { status: value, page: 0 });
    this.load(role);
  }

  goToPage(page: number): void {
    const role = this.activeTab().role;
    this.patchTab(role, { page });
    this.load(role);
  }

  load(role: RoleKind): void {
    const state = this.tabState()[role];
    this.patchTab(role, { loading: true, error: null });
    this.userService
      .list({ role, status: state.status || undefined, q: state.q || undefined, page: state.page, size: PAGE_SIZE })
      .subscribe({
        next: (paged) =>
          this.patchTab(role, {
            users: paged.content,
            totalElements: paged.totalElements,
            totalPages: paged.totalPages,
            loading: false,
          }),
        error: () => this.patchTab(role, { loading: false, error: 'Could not load users.' }),
      });
  }

  loadSummary(): void {
    this.userService.summary().subscribe({ next: (s) => this.summary.set(s) });
  }

  onUserCreated(created: CreatedUserResponse): void {
    this.showNewUserDialog.set(false);
    this.load(this.activeTab().role);
    this.loadSummary();
    this.showToast(`${created.name} added as ${created.role}.`);
  }

  onRoleChanged(updated: UserResponse): void {
    this.roleDialogTarget.set(null);
    // the user may now belong to a different tab than the one it was changed from
    for (const tab of this.tabs) {
      this.load(tab.role);
    }
    this.loadSummary();
    this.showToast(`${updated.name}'s role is now ${updated.role}.`);
  }

  cycleStatus(user: UserResponse): void {
    const next: Record<UserStatus, UserStatus> = {
      ACTIVE: 'INACTIVE',
      INACTIVE: 'SUSPENDED',
      SUSPENDED: 'ACTIVE',
    };
    const newStatus = next[user.status];
    this.userService.updateStatus(user.id, newStatus).subscribe({
      next: (updated) => {
        this.load(this.activeTab().role);
        this.loadSummary();
        this.showToast(`${updated.name} is now ${updated.status.toLowerCase()}.`);
      },
      error: (err: HttpErrorResponse) => {
        this.showToast(err.error?.message ?? 'Could not update status.', true);
      },
    });
  }

  private patchTab(role: RoleKind, patch: Partial<TabState>): void {
    this.tabState.update((all) => ({ ...all, [role]: { ...all[role], ...patch } }));
  }

  private showToast(message: string, error = false): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
