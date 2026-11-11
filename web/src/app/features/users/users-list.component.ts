import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CreatedUserResponse, RoleKind, UserResponse, UserStatus, UserSummary } from '../../core/models/user.model';
import { RoleResponse } from '../../core/models/roles.model';
import { RolesService } from '../../core/services/roles.service';
import { UserService } from '../../core/services/user.service';
import { ChangeRoleDialogComponent } from './change-role-dialog.component';
import { NewUserDialogComponent } from './new-user-dialog.component';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, statusOptions } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/** Plurals for the three seeded account kinds read naturally as tab labels; any later
 * custom role (e.g. "Program Head") falls back to a plain "+ s", which is also fine. */
const PLURAL_OVERRIDES: Record<string, string> = {
  Administrator: 'Administrators',
  Lecturer: 'Lecturers',
  Student: 'Students',
};
function pluralize(name: string): string {
  return PLURAL_OVERRIDES[name] ?? `${name}s`;
}

interface RoleTab {
  id: string;
  name: string;
  kind: RoleKind;
  label: string;
  addLabel: string;
}

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

/** One tab per active system role (see Roles & Permissions) — not just the three account
 * kinds, so a custom role like "Program Head" gets its own tab, listing everyone who
 * currently holds it (by account role or by scope assignment) rather than being buried
 * inside a generic "Lecturers" list. */
@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [FormsModule, NewUserDialogComponent, ChangeRoleDialogComponent, SearchableSelectComponent, SearchInputComponent, RowMenuComponent, FilterBarComponent, PagerComponent],
  templateUrl: './users-list.component.html',
})
export class UsersListComponent implements OnInit {
  readonly tabsLoading = signal(true);
  readonly tabs = signal<RoleTab[]>([]);
  readonly activeTabId = signal<string>('');
  readonly tabState = signal<Record<string, TabState>>({});

  readonly summary = signal<UserSummary | null>(null);
  readonly showNewUserDialog = signal(false);
  readonly roleDialogTarget = signal<UserResponse | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  private searchDebounce: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private userService: UserService,
    private rolesService: RolesService,
  ) {}

  ngOnInit(): void {
    this.rolesService.roles().subscribe({
      next: (roles: RoleResponse[]) => {
        const tabs = roles.map((r) => ({
          id: r.id,
          name: r.name,
          kind: r.kind,
          label: pluralize(r.name),
          addLabel: `+ New ${r.name}`,
        }));
        this.tabs.set(tabs);
        this.tabState.set(Object.fromEntries(tabs.map((t) => [t.id, freshTabState()])));
        this.tabsLoading.set(false);
        if (tabs.length) {
          this.activeTabId.set(tabs[0].id);
          for (const tab of tabs) this.load(tab.id);
        }
      },
      error: () => this.tabsLoading.set(false),
    });
    this.loadSummary();
  }

  activeTab(): RoleTab {
    return this.tabs().find((t) => t.id === this.activeTabId()) ?? this.tabs()[0];
  }

  selectTab(tab: RoleTab): void {
    this.activeTabId.set(tab.id);
  }

  current(): TabState {
    return this.tabState()[this.activeTabId()] ?? freshTabState();
  }

  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    // Every tab is loaded up front and cached across tab switches, so a page-size change
    // has to reload every tab now, not just the visible one — otherwise switching tabs
    // later would show pages fetched with the old size.
    for (const tab of this.tabs()) {
      this.patchTab(tab.id, { page: 0 });
      this.load(tab.id);
    }
  }

  onSearchInput(value: string): void {
    const id = this.activeTabId();
    this.patchTab(id, { q: value, page: 0 });
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(id), SEARCH_DEBOUNCE_MS);
  }

  onStatusFilterChange(value: UserStatus | ''): void {
    const id = this.activeTabId();
    this.patchTab(id, { status: value, page: 0 });
    this.load(id);
  }

  goToPage(page: number): void {
    const id = this.activeTabId();
    this.patchTab(id, { page });
    this.load(id);
  }

  load(roleId: string): void {
    const state = this.tabState()[roleId];
    if (!state) return;
    this.patchTab(roleId, { loading: true, error: null });
    this.userService
      .list({
        roleId,
        status: state.status || undefined,
        q: state.q || undefined,
        page: state.page,
        size: this.pageSize(),
      })
      .subscribe({
        next: (paged) =>
          this.patchTab(roleId, {
            users: paged.content,
            totalElements: paged.totalElements,
            totalPages: paged.totalPages,
            loading: false,
          }),
        error: () => this.patchTab(roleId, { loading: false, error: 'Could not load users.' }),
      });
  }

  loadSummary(): void {
    this.userService.summary().subscribe({ next: (s) => this.summary.set(s) });
  }

  onUserCreated(created: CreatedUserResponse): void {
    this.showNewUserDialog.set(false);
    this.load(this.activeTabId());
    this.loadSummary();
    this.showToast(`${created.name} added as ${created.role}.`);
  }

  onRoleChanged(updated: UserResponse): void {
    this.roleDialogTarget.set(null);
    // the user may now belong to a different tab than the one it was changed from
    for (const tab of this.tabs()) this.load(tab.id);
    this.loadSummary();
    this.showToast(`${updated.name}'s role is now ${updated.roleName}.`);
  }

  readonly statusFilterOptions = statusOptions(['ACTIVE', 'INACTIVE', 'SUSPENDED']);

  activeFilterCount(): number {
    return countActive(this.current().q.trim(), this.current().status);
  }

  filterSummary(): string {
    const c = this.current();
    if (c.loading || c.error) return '';
    return `${c.totalElements} ${this.activeTab()?.label.toLowerCase()}${this.activeFilterCount() ? ' match' : ' in total'}`;
  }

  clearFilters(): void {
    const id = this.activeTabId();
    clearTimeout(this.searchDebounce);
    this.patchTab(id, { q: '', status: '', page: 0 });
    this.load(id);
  }

  setStatus(user: UserResponse, newStatus: UserStatus): void {
    if (newStatus === 'SUSPENDED' && !confirm(`Suspend ${user.name}? They won't be able to sign in.`)) return;
    this.userService.updateStatus(user.id, newStatus).subscribe({
      next: (updated) => {
        this.load(this.activeTabId());
        this.loadSummary();
        this.showToast(`${updated.name} is now ${updated.status.toLowerCase()}.`);
      },
      error: (err: HttpErrorResponse) => {
        this.showToast(err.error?.message ?? 'Could not update status.', true);
      },
    });
  }

  private patchTab(roleId: string, patch: Partial<TabState>): void {
    this.tabState.update((all) => ({ ...all, [roleId]: { ...all[roleId], ...patch } }));
  }

  private showToast(message: string, error = false): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
