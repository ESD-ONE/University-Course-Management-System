import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { UserService } from './user.service';
import { RolesService } from './roles.service';
import { KEYCLOAK } from '../config/keycloak';
import { AssignmentResponse, ProfileResponse, RoleKind } from '../models/user.model';
import { EffectivePermission } from '../models/roles.model';

/**
 * AuthService was refactored so its keycloak-js instance arrives through the KEYCLOAK
 * injection token, letting us hand it a fake here. These tests pin the session lifecycle
 * (init/login/freshToken/logout/expiry) and the UI permission checks.
 */
describe('AuthService', () => {
  const base = '/api/identity';

  let service: AuthService;
  let httpMock: HttpTestingController;
  let userService: jasmine.SpyObj<UserService>;
  let rolesService: jasmine.SpyObj<RolesService>;
  let router: jasmine.SpyObj<Router> & { navigated: boolean };
  let keycloak: FakeKeycloak;

  /** The slice of keycloak-js AuthService actually touches. */
  interface FakeKeycloak {
    token: string | undefined;
    authenticated: boolean;
    onTokenExpired: (() => void) | undefined;
    init: jasmine.Spy;
    login: jasmine.Spy;
    logout: jasmine.Spy;
    updateToken: jasmine.Spy;
    clearToken: jasmine.Spy;
  }

  function profile(role: RoleKind = 'ADMIN', assignments: AssignmentResponse[] = []): ProfileResponse {
    return {
      id: 'u1',
      name: 'Test User',
      email: 'test@ucms.local',
      role,
      status: 'ACTIVE',
      studentId: null,
      roleId: 'r1',
      roleName: role,
      notifyEmail: true,
      notifyAssignments: true,
      notifyGrades: true,
      notifyRegistrations: true,
      assignments,
    };
  }

  function perm(partial: Partial<EffectivePermission>): EffectivePermission {
    return { scopeType: 'GLOBAL', scopeId: null, module: 'Users', actionKey: 'View', ...partial } as EffectivePermission;
  }

  beforeEach(() => {
    userService = jasmine.createSpyObj<UserService>('UserService', ['me']);
    rolesService = jasmine.createSpyObj<RolesService>('RolesService', ['myPermissions']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']) as jasmine.SpyObj<Router> & { navigated: boolean };
    router.navigated = true;
    keycloak = {
      token: undefined,
      authenticated: false,
      onTokenExpired: undefined,
      init: jasmine.createSpy('init').and.resolveTo(false),
      login: jasmine.createSpy('login').and.resolveTo(undefined),
      logout: jasmine.createSpy('logout').and.resolveTo(undefined),
      updateToken: jasmine.createSpy('updateToken').and.resolveTo(true),
      clearToken: jasmine.createSpy('clearToken'),
    };

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserService, useValue: userService },
        { provide: RolesService, useValue: rolesService },
        { provide: Router, useValue: router },
        { provide: KEYCLOAK, useValue: keycloak },
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('init()', () => {
    it('loads profile + permissions and marks authenticated when a session exists', async () => {
      keycloak.init.and.resolveTo(true);
      userService.me.and.returnValue(of(profile('LECTURER')));
      rolesService.myPermissions.and.returnValue(of([perm({ module: 'Courses' })]));

      await service.init();

      expect(service.isAuthenticated()).toBeTrue();
      expect(service.currentUser()?.role).toBe('LECTURER');
      expect(service.permissions().length).toBe(1);
      expect(keycloak.init).toHaveBeenCalled();
    });

    it('stays signed out when there is no session', async () => {
      keycloak.init.and.resolveTo(false);
      await service.init();
      expect(service.isAuthenticated()).toBeFalse();
      expect(userService.me).not.toHaveBeenCalled();
    });

    it('tolerates a failing permissions fetch (treats it as none)', async () => {
      keycloak.init.and.resolveTo(true);
      userService.me.and.returnValue(of(profile()));
      rolesService.myPermissions.and.returnValue(throwError(() => new Error('down')));

      await service.init();

      expect(service.isAuthenticated()).toBeTrue();
      expect(service.permissions()).toEqual([]);
    });
  });

  describe('login()', () => {
    it('redirects to Keycloak, returning to the given path', () => {
      service.login('/app/users');
      expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: `${location.origin}/app/users` });
    });

    it('defaults the return path to /app', () => {
      service.login();
      expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: `${location.origin}/app` });
    });
  });

  describe('freshToken()', () => {
    it('returns null without refreshing when there is no session', async () => {
      keycloak.authenticated = false;
      expect(await service.freshToken()).toBeNull();
      expect(keycloak.updateToken).not.toHaveBeenCalled();
    });

    it('refreshes and returns the current token', async () => {
      keycloak.authenticated = true;
      keycloak.token = 'fresh-tok';
      expect(await service.freshToken()).toBe('fresh-tok');
      expect(keycloak.updateToken).toHaveBeenCalledWith(30);
    });

    it('ends the session and returns null when the refresh fails', async () => {
      keycloak.authenticated = true;
      keycloak.updateToken.and.rejectWith(new Error('expired'));
      const expired = spyOn(service, 'sessionExpired');
      expect(await service.freshToken()).toBeNull();
      expect(expired).toHaveBeenCalled();
    });
  });

  describe('logout()', () => {
    it('revokes the token at the gateway, clears state, and ends the Keycloak session', async () => {
      keycloak.token = 'tok-9';
      service.setCurrentUser(profile());

      const done = service.logout();
      const req = httpMock.expectOne(`${base}/auth/logout`);
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Authorization')).toBe('Bearer tok-9');
      req.flush({});
      await done;

      expect(service.currentUser()).toBeNull();
      expect(service.permissions()).toEqual([]);
      expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: `${location.origin}/login` });
    });

    it('still ends the session when there is no token to revoke', async () => {
      keycloak.token = undefined;
      await service.logout();
      httpMock.expectNone(`${base}/auth/logout`);
      expect(keycloak.logout).toHaveBeenCalled();
    });
  });

  describe('sessionExpired() / consumeExpiredNotice()', () => {
    it('navigates straight to /login?expired=1 once the router is active', () => {
      router.navigated = true;
      service.setCurrentUser(profile());
      service.sessionExpired();
      expect(keycloak.clearToken).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
      expect(service.currentUser()).toBeNull();
    });

    it('defers to a consumable notice when the router has not navigated yet', () => {
      router.navigated = false;
      service.setCurrentUser(profile());
      service.sessionExpired();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(service.consumeExpiredNotice()).toBeTrue();
      // the notice is one-shot
      expect(service.consumeExpiredNotice()).toBeFalse();
    });

    it('is a no-op when there was no session to end', () => {
      router.navigated = true;
      keycloak.token = undefined;
      service.sessionExpired();
      expect(keycloak.clearToken).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('can() + hasDepartmentScope', () => {
    async function initWith(perms: EffectivePermission[], assignments: AssignmentResponse[] = []) {
      keycloak.init.and.resolveTo(true);
      userService.me.and.returnValue(of(profile('LECTURER', assignments)));
      rolesService.myPermissions.and.returnValue(of(perms));
      await service.init();
    }

    it('grants a GLOBAL permission regardless of scope argument', async () => {
      await initWith([perm({ module: 'Users', actionKey: 'View', scopeType: 'GLOBAL' })]);
      expect(service.can('Users', 'View')).toBeTrue();
      expect(service.can('Users', 'View', 'any-scope')).toBeTrue();
    });

    it('honours scope for a non-GLOBAL permission', async () => {
      await initWith([perm({ module: 'Courses', actionKey: 'Edit', scopeType: 'DEPARTMENT', scopeId: 'd1' })]);
      expect(service.can('Courses', 'Edit')).toBeTrue();
      expect(service.can('Courses', 'Edit', 'd1')).toBeTrue();
      expect(service.can('Courses', 'Edit', 'd2')).toBeFalse();
    });

    it('denies an ungranted (module, action)', async () => {
      await initWith([perm({ module: 'Users', actionKey: 'View' })]);
      expect(service.can('Users', 'Delete')).toBeFalse();
    });

    it('hasDepartmentScope reflects a DEPARTMENT-scoped assignment', async () => {
      const assignment = { id: 'a1', roleId: 'r1', roleName: 'Dept Head', scopeType: 'DEPARTMENT', scopeId: 'd1' } as AssignmentResponse;
      await initWith([], [assignment]);
      expect(service.hasDepartmentScope()).toBeTrue();
    });

    it('hasDepartmentScope is false with no department assignment', async () => {
      await initWith([], []);
      expect(service.hasDepartmentScope()).toBeFalse();
    });
  });
});
