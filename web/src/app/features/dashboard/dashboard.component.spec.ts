import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';
import { AnalyticsService } from '../../core/services/analytics.service';
import { GradingService } from '../../core/services/grading.service';
import { AdminDashboard, LecturerDashboard, StudentDashboard } from '../../core/models/analytics.model';
import { ProfileResponse, RoleKind } from '../../core/models/user.model';

describe('DashboardComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let analytics: jasmine.SpyObj<AnalyticsService>;

  function asRole(role: RoleKind | null) {
    auth.currentUser.and.returnValue(role ? ({ role } as ProfileResponse) : null);
  }
  function make(): DashboardComponent {
    return TestBed.createComponent(DashboardComponent).componentInstance;
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    analytics = jasmine.createSpyObj<AnalyticsService>('AnalyticsService', [
      'adminDashboard',
      'lecturerDashboard',
      'studentDashboard',
      'refreshAdminDashboard',
    ]);
    analytics.adminDashboard.and.returnValue(of({} as AdminDashboard));
    analytics.lecturerDashboard.and.returnValue(of({} as LecturerDashboard));
    analytics.studentDashboard.and.returnValue(of({} as StudentDashboard));
    asRole('STUDENT');
    // The dashboard also loads the "coming up" feed on construction.
    const grading = jasmine.createSpyObj<GradingService>('GradingService', ['upcoming']);
    grading.upcoming.and.returnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: AnalyticsService, useValue: analytics },
        { provide: GradingService, useValue: grading },
      ],
    });
  });

  it('defaults the role to STUDENT when no user is loaded', () => {
    asRole(null);
    const c = make();
    expect(c.role()).toBe('STUDENT');
  });

  it('loads the admin dashboard for an ADMIN and clears loading', () => {
    asRole('ADMIN');
    analytics.adminDashboard.and.returnValue(of({ studentsByProgram: [] } as unknown as AdminDashboard));
    const c = make();
    expect(analytics.adminDashboard).toHaveBeenCalled();
    expect(c.admin()).not.toBeNull();
    expect(c.loading()).toBeFalse();
  });

  it('loads the lecturer dashboard for a LECTURER', () => {
    asRole('LECTURER');
    const c = make();
    expect(analytics.lecturerDashboard).toHaveBeenCalled();
    expect(c.lecturer()).not.toBeNull();
    expect(analytics.studentDashboard).not.toHaveBeenCalled();
  });

  it('loads the student dashboard otherwise', () => {
    asRole('STUDENT');
    const c = make();
    expect(analytics.studentDashboard).toHaveBeenCalled();
  });

  it('records a load error and stops loading', () => {
    asRole('ADMIN');
    analytics.adminDashboard.and.returnValue(throwError(() => new Error('boom')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
    expect(c.loading()).toBeFalse();
  });

  it('refreshAdmin replaces the snapshot and clears the refreshing flag', () => {
    asRole('ADMIN');
    const refreshed = { topCourses: [] } as unknown as AdminDashboard;
    analytics.refreshAdminDashboard.and.returnValue(of(refreshed));
    const c = make();
    c.refreshAdmin();
    expect(c.admin()).toBe(refreshed);
    expect(c.refreshing()).toBeFalse();
  });

  it('gradedPct rounds graded/submitted and handles zero submissions', () => {
    const c = make();
    expect(c.gradedPct({ graded: 3, submitted: 4 })).toBe(75);
    expect(c.gradedPct({ graded: 0, submitted: 0 })).toBe(0);
  });

  it('creditRatio is completed/required, clamped to 1', () => {
    analytics.studentDashboard.and.returnValue(
      of({ creditsCompleted: 30, creditsRequired: 120 } as unknown as StudentDashboard),
    );
    const c = make();
    expect(c.creditRatio()).toBeCloseTo(0.25, 5);
  });

  it('ring() produces a conic-gradient string', () => {
    const c = make();
    expect(c.ring()).toContain('conic-gradient');
  });
});
