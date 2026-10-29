import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DepartmentResponse } from '../../core/models/reference-data.model';
import { AssignmentResponse, PermissionResponse, RoleKind, RoleResponse, ScopeType } from '../../core/models/roles.model';
import { UserResponse } from '../../core/models/user.model';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { RolesService } from '../../core/services/roles.service';
import { UserService } from '../../core/services/user.service';
import { errorMessage, Toaster } from '../../shared/ui';

const ACTION_ORDER = ['View', 'Create', 'Edit', 'Delete', 'manage'];

/**
 * Wireframe adminRoles, backed by identity-access-service's existing roles / permission
 * matrix / scope-assignment API. Matrix toggles take effect in academic-structure-service
 * within its 60-second role-catalog sync. A DEPARTMENT-scoped assignment is what makes a
 * lecturer a "Department Head" for that department.
 */
@Component({
  selector: 'app-roles-permissions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './roles-permissions.component.html',
})
export class RolesPermissionsComponent {
  private rolesService = inject(RolesService);
  private userService = inject(UserService);
  private referenceData = inject(ReferenceDataService);

  readonly roles = signal<RoleResponse[]>([]);
  readonly permissions = signal<PermissionResponse[]>([]);
  readonly departments = signal<DepartmentResponse[]>([]);
  readonly selectedRoleId = signal<string>('');
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly savingCell = signal<string | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly newRoleOpen = signal(false);
  readonly newRoleName = signal('');
  readonly newRoleKind = signal<RoleKind>('LECTURER');
  readonly newRoleError = signal<string | null>(null);

  readonly userQuery = signal('');
  readonly users = signal<UserResponse[]>([]);
  readonly selectedUser = signal<UserResponse | null>(null);
  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly assignRoleId = signal('');
  readonly assignScope = signal<ScopeType>('DEPARTMENT');
  readonly assignDepartmentId = signal('');
  private userSearch$ = new Subject<string>();

  readonly selectedRole = computed(() => this.roles().find((r) => r.id === this.selectedRoleId()) ?? null);
  readonly actions = computed(() =>
    ACTION_ORDER.filter((a) => this.permissions().some((p) => p.actionKey === a)),
  );
  readonly modules = computed(() => [...new Set(this.permissions().map((p) => p.module))]);
  readonly departmentName = (id: string | null) => this.departments().find((d) => d.id === id)?.name ?? id ?? '';

  constructor() {
    this.load();
    this.referenceData.listDepartments().subscribe({ next: (v) => this.departments.set(v.filter((d) => d.status === 'ACTIVE')) });
    this.userSearch$
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((q) => this.userService.list({ q, size: 10 })))
      .subscribe({ next: (page) => this.users.set(page.content), error: () => this.users.set([]) });
  }

  load(): void {
    this.rolesService.permissions().subscribe({ next: (v) => this.permissions.set(v) });
    this.rolesService.roles().subscribe({
      next: (v) => {
        this.roles.set(v);
        if (!this.selectedRoleId() && v.length) this.selectedRoleId.set(v[0].id);
        if (!this.assignRoleId() && v.length) this.assignRoleId.set(v[0].id);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load roles.'));
        this.loading.set(false);
      },
    });
  }

  permissionFor(module: string, action: string): PermissionResponse | undefined {
    return this.permissions().find((p) => p.module === module && p.actionKey === action);
  }

  /** The V2 seed's coarse permissions use lower-case module names ("users"/"roles" with a
   *  single "manage" action), distinct from the later per-action matrix — label them so
   *  "users" isn't mistaken for a duplicate of "Users". */
  moduleLabel(module: string): string {
    const title = module.charAt(0).toUpperCase() + module.slice(1);
    return module === module.toLowerCase() ? `${title} (legacy, all-or-nothing)` : module;
  }

  grantedCount(role: RoleResponse): number {
    return role.permissions.filter((g) => g.granted).length;
  }

  granted(permissionId: string): boolean {
    return this.selectedRole()?.permissions.some((g) => g.permissionId === permissionId && g.granted) ?? false;
  }

  toggle(permission: PermissionResponse): void {
    const role = this.selectedRole();
    if (!role) return;
    const next = !this.granted(permission.id);
    this.savingCell.set(permission.id);
    this.rolesService.setGrant(role.id, permission.id, next).subscribe({
      next: (updated) => {
        this.savingCell.set(null);
        this.roles.update((rs) => rs.map((r) => (r.id === updated.id ? updated : r)));
      },
      error: (err) => {
        this.savingCell.set(null);
        this.toaster.show(errorMessage(err, 'Could not update this permission.'), true);
      },
    });
  }

  createRole(): void {
    const name = this.newRoleName().trim();
    if (!name) return;
    this.newRoleError.set(null);
    this.rolesService.createRole(name, this.newRoleKind()).subscribe({
      next: (role) => {
        this.newRoleOpen.set(false);
        this.newRoleName.set('');
        this.roles.update((rs) => [...rs, role]);
        this.selectedRoleId.set(role.id);
        this.toaster.show(`Role "${role.name}" created — grant it permissions below.`);
      },
      error: (err) => this.newRoleError.set(errorMessage(err, 'Could not create this role.')),
    });
  }

  searchUsers(q: string): void {
    this.userQuery.set(q);
    this.userSearch$.next(q);
  }

  selectUser(user: UserResponse): void {
    this.selectedUser.set(user);
    this.rolesService.assignments(user.id).subscribe({ next: (v) => this.assignments.set(v) });
  }

  assign(): void {
    const user = this.selectedUser();
    if (!user || !this.assignRoleId()) return;
    const scopeId = this.assignScope() === 'GLOBAL' ? null : this.assignDepartmentId() || null;
    if (this.assignScope() === 'DEPARTMENT' && !scopeId) {
      this.toaster.show('Choose a department for a department-scoped assignment.', true);
      return;
    }
    this.rolesService.assign(user.id, this.assignRoleId(), this.assignScope(), scopeId).subscribe({
      next: () => {
        this.toaster.show(`Assignment added for ${user.name} — applies at their next sign-in.`);
        this.selectUser(user);
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not add this assignment.'), true),
    });
  }

  revoke(a: AssignmentResponse): void {
    const user = this.selectedUser();
    if (!user || !confirm(`Revoke ${a.roleName} (${a.scopeType.toLowerCase()}) from ${user.name}?`)) return;
    this.rolesService.revoke(a.id).subscribe({
      next: () => this.selectUser(user),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not revoke this assignment.'), true),
    });
  }
}
