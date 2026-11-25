import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { UsersListComponent } from './users-list.component';
import { UserService } from '../../core/services/user.service';
import { RolesService } from '../../core/services/roles.service';
import { RoleResponse } from '../../core/models/roles.model';
import { UserResponse, UserStatus, UserSummary } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('UsersListComponent', () => {
  let userService: jasmine.SpyObj<UserService>;
  let rolesService: jasmine.SpyObj<RolesService>;

  const roles = [
    { id: 'r-admin', name: 'Administrator', kind: 'ADMIN' },
    { id: 'r-lect', name: 'Lecturer', kind: 'LECTURER' },
  ] as RoleResponse[];

  function paged(users: UserResponse[], total = users.length) {
    return of({ content: users, totalElements: total, totalPages: 1, page: 0, size: 20 } as unknown as PagedResponse<UserResponse>);
  }

  function make(): UsersListComponent {
    const c = TestBed.createComponent(UsersListComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    userService = jasmine.createSpyObj<UserService>('UserService', ['list', 'summary', 'updateStatus']);
    rolesService = jasmine.createSpyObj<RolesService>('RolesService', ['roles']);
    rolesService.roles.and.returnValue(of(roles));
    userService.list.and.returnValue(paged([{ id: 'u1', name: 'Ada' } as UserResponse]));
    userService.summary.and.returnValue(of({ total: 1 } as unknown as UserSummary));

    TestBed.configureTestingModule({
      providers: [
        { provide: UserService, useValue: userService },
        { provide: RolesService, useValue: rolesService },
      ],
    });
  });

  it('builds one tab per role with pluralized labels and loads each', () => {
    const c = make();
    expect(c.tabs().map((t) => t.label)).toEqual(['Administrators', 'Lecturers']);
    expect(c.activeTabId()).toBe('r-admin');
    // one list() call per tab, each scoped by roleId
    expect(userService.list).toHaveBeenCalledTimes(2);
    expect(c.current().users.length).toBe(1);
  });

  it('selectTab switches the active tab', () => {
    const c = make();
    c.selectTab(c.tabs()[1]);
    expect(c.activeTabId()).toBe('r-lect');
    expect(c.activeTab().name).toBe('Lecturer');
  });

  it('onStatusFilterChange patches the tab and reloads with the status', () => {
    const c = make();
    userService.list.calls.reset();
    c.onStatusFilterChange('SUSPENDED');
    expect(c.current().status).toBe('SUSPENDED');
    expect(userService.list).toHaveBeenCalledWith(jasmine.objectContaining({ status: 'SUSPENDED', roleId: 'r-admin' }));
  });

  it('onSearchInput debounces before reloading', fakeAsync(() => {
    const c = make();
    userService.list.calls.reset();
    c.onSearchInput('ada');
    expect(userService.list).not.toHaveBeenCalled(); // still within debounce
    tick(300);
    expect(userService.list).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'ada' }));
  }));

  it('clearFilters resets q + status and reloads', () => {
    const c = make();
    c.onStatusFilterChange('ACTIVE');
    userService.list.calls.reset();
    c.clearFilters();
    expect(c.current().status).toBe('');
    expect(c.current().q).toBe('');
    expect(userService.list).toHaveBeenCalled();
  });

  it('activeFilterCount counts a query and a status filter', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.onStatusFilterChange('ACTIVE');
    expect(c.activeFilterCount()).toBe(1);
  });

  it('setStatus confirms before suspending and updates on confirm', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    userService.updateStatus.and.returnValue(of({ name: 'Ada', status: 'SUSPENDED' } as UserResponse));
    const c = make();
    c.setStatus({ id: 'u1', name: 'Ada' } as UserResponse, 'SUSPENDED');
    expect(window.confirm).toHaveBeenCalled();
    expect(userService.updateStatus).toHaveBeenCalledWith('u1', 'SUSPENDED');
  });

  it('setStatus aborts a suspend when the confirm is declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const c = make();
    c.setStatus({ id: 'u1', name: 'Ada' } as UserResponse, 'SUSPENDED');
    expect(userService.updateStatus).not.toHaveBeenCalled();
  });

  it('setStatus does not confirm for a non-suspend change', () => {
    const confirmSpy = spyOn(window, 'confirm');
    userService.updateStatus.and.returnValue(of({ name: 'Ada', status: 'ACTIVE' } as UserResponse));
    const c = make();
    c.setStatus({ id: 'u1', name: 'Ada' } as UserResponse, 'ACTIVE' as UserStatus);
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(userService.updateStatus).toHaveBeenCalledWith('u1', 'ACTIVE');
  });

  it('onRoleChanged reloads every tab and shows a toast', () => {
    const c = make();
    userService.list.calls.reset();
    c.onRoleChanged({ name: 'Ada', roleName: 'Lecturer' } as UserResponse);
    expect(userService.list).toHaveBeenCalledTimes(2); // both tabs reloaded
    expect(c.toast()?.message).toContain('role is now Lecturer');
  });
});
