import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import {
  adminGuard,
  authGuard,
  departmentScopedGuard,
  guestGuard,
  lecturerGuard,
  staffGuard,
  studentGuard,
} from './auth.guard';
import { AuthService } from '../services/auth.service';
import { AssignmentResponse, ProfileResponse, RoleKind } from '../models/user.model';

/**
 * The guards are the frontend's first authorization gate, so they're the highest-value
 * thing to pin down. They're pure functions over AuthService state + Router, so a stub
 * AuthService and a real Router (for createUrlTree) is all they need.
 */
describe('route guards', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  /** A minimal signed-in profile; override role/assignments per test. */
  function profile(role: RoleKind, assignments: AssignmentResponse[] = []): ProfileResponse {
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

  function run(guard: CanActivateFn): boolean | UrlTree {
    // Guards use inject(), so they must run inside an injection context.
    return TestBed.runInInjectionContext(() => guard({} as never, {} as never)) as boolean | UrlTree;
  }

  /** The URL a returned UrlTree points at, for asserting redirects. */
  function redirectPath(result: boolean | UrlTree): string {
    expect(result instanceof UrlTree).toBeTrue();
    return router.serializeUrl(result as UrlTree).split('?')[0];
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', [
      'isAuthenticated',
      'consumeExpiredNotice',
      'currentUser',
      'hasDepartmentScope',
    ]);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
    router = TestBed.inject(Router);
  });

  describe('authGuard', () => {
    it('allows an authenticated user', () => {
      auth.isAuthenticated.and.returnValue(true);
      expect(run(authGuard)).toBeTrue();
    });

    it('redirects an anonymous user to /login', () => {
      auth.isAuthenticated.and.returnValue(false);
      auth.consumeExpiredNotice.and.returnValue(false);
      expect(redirectPath(run(authGuard))).toBe('/login');
    });

    it('adds ?expired=1 when an expiry notice is pending', () => {
      auth.isAuthenticated.and.returnValue(false);
      auth.consumeExpiredNotice.and.returnValue(true);
      const tree = run(authGuard) as UrlTree;
      expect(router.serializeUrl(tree)).toContain('expired=1');
    });
  });

  describe('guestGuard', () => {
    it('allows an anonymous visitor', () => {
      auth.isAuthenticated.and.returnValue(false);
      expect(run(guestGuard)).toBeTrue();
    });

    it('sends an authenticated user to /app', () => {
      auth.isAuthenticated.and.returnValue(true);
      expect(redirectPath(run(guestGuard))).toBe('/app');
    });
  });

  describe('role guards', () => {
    const cases: { guard: CanActivateFn; name: string; allowed: RoleKind; denied: RoleKind }[] = [
      { guard: adminGuard, name: 'adminGuard', allowed: 'ADMIN', denied: 'STUDENT' },
      { guard: lecturerGuard, name: 'lecturerGuard', allowed: 'LECTURER', denied: 'STUDENT' },
      { guard: studentGuard, name: 'studentGuard', allowed: 'STUDENT', denied: 'ADMIN' },
    ];

    for (const c of cases) {
      it(`${c.name} allows ${c.allowed} and redirects ${c.denied} to /app`, () => {
        auth.currentUser.and.returnValue(profile(c.allowed));
        expect(run(c.guard)).toBeTrue();

        auth.currentUser.and.returnValue(profile(c.denied));
        expect(redirectPath(run(c.guard))).toBe('/app');
      });
    }

    it('role guard redirects when there is no signed-in user', () => {
      auth.currentUser.and.returnValue(null);
      expect(redirectPath(run(adminGuard))).toBe('/app');
    });
  });

  describe('staffGuard', () => {
    it('allows ADMIN and LECTURER, denies STUDENT', () => {
      auth.currentUser.and.returnValue(profile('ADMIN'));
      expect(run(staffGuard)).toBeTrue();

      auth.currentUser.and.returnValue(profile('LECTURER'));
      expect(run(staffGuard)).toBeTrue();

      auth.currentUser.and.returnValue(profile('STUDENT'));
      expect(redirectPath(run(staffGuard))).toBe('/app');
    });
  });

  describe('departmentScopedGuard', () => {
    it('allows any ADMIN', () => {
      auth.currentUser.and.returnValue(profile('ADMIN'));
      auth.hasDepartmentScope.and.returnValue(false);
      expect(run(departmentScopedGuard)).toBeTrue();
    });

    it('allows a non-admin holding a DEPARTMENT scope', () => {
      auth.currentUser.and.returnValue(profile('LECTURER'));
      auth.hasDepartmentScope.and.returnValue(true);
      expect(run(departmentScopedGuard)).toBeTrue();
    });

    it('denies a lecturer with no department scope', () => {
      auth.currentUser.and.returnValue(profile('LECTURER'));
      auth.hasDepartmentScope.and.returnValue(false);
      expect(redirectPath(run(departmentScopedGuard))).toBe('/app');
    });
  });
});
