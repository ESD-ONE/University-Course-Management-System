import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminDashboard, LecturerDashboard, StudentDashboard } from '../../core/models/analytics.model';
import { REGISTRATION_ACTION_LABELS } from '../../core/models/enrollment.model';
import { AnalyticsService } from '../../core/services/analytics.service';
import { AuthService } from '../../core/services/auth.service';
import { donutGradient, errorMessage, initials, percentOf, Segment, withColors } from '../../shared/ui';

/**
 * Role dashboards from analytics-reporting-service (wireframe adminDashboard /
 * lecturerDashboard / studentDashboard). The admin view is a precomputed snapshot
 * (refreshed every few minutes and on enrollment/grading events); lecturer and student
 * views are computed live for the signed-in user.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  readonly auth = inject(AuthService);
  private analytics = inject(AnalyticsService);

  readonly role = computed(() => this.auth.currentUser()?.role ?? 'STUDENT');
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly refreshing = signal(false);

  readonly admin = signal<AdminDashboard | null>(null);
  readonly lecturer = signal<LecturerDashboard | null>(null);
  readonly student = signal<StudentDashboard | null>(null);

  readonly donutGradient = donutGradient;
  readonly percentOf = percentOf;
  readonly initials = initials;
  readonly actionLabel = (a: string) => REGISTRATION_ACTION_LABELS[a] ?? a;

  readonly programSegments = computed<Segment[]>(() => withColors(this.admin()?.studentsByProgram ?? []));
  readonly trendMax = computed(() => Math.max(1, ...(this.admin()?.enrollmentTrend ?? []).map((p) => p.value)));
  readonly topMax = computed(() => Math.max(1, ...(this.admin()?.topCourses ?? []).map((c) => c.enrolled)));
  readonly gradingSegments = computed<Segment[]>(() => {
    const d = this.lecturer();
    return d
      ? [
          { label: 'Graded', value: d.graded, color: '#1F8A5F' },
          { label: 'Pending', value: d.pending, color: '#B4791F' },
          { label: 'Not submitted', value: d.notSubmitted, color: '#8991A8' },
        ]
      : [];
  });
  readonly creditRatio = computed(() => {
    const s = this.student();
    return s?.creditsRequired ? Math.min(1, s.creditsCompleted / s.creditsRequired) : 0;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const done = () => this.loading.set(false);
    const fail = (err: unknown) => {
      this.loadError.set(errorMessage(err, 'Could not load your dashboard.'));
      this.loading.set(false);
    };
    switch (this.role()) {
      case 'ADMIN':
        this.analytics.adminDashboard().subscribe({ next: (v) => { this.admin.set(v); done(); }, error: fail });
        break;
      case 'LECTURER':
        this.analytics.lecturerDashboard().subscribe({ next: (v) => { this.lecturer.set(v); done(); }, error: fail });
        break;
      default:
        this.analytics.studentDashboard().subscribe({ next: (v) => { this.student.set(v); done(); }, error: fail });
    }
  }

  refreshAdmin(): void {
    this.refreshing.set(true);
    this.analytics.refreshAdminDashboard().subscribe({
      next: (v) => {
        this.admin.set(v);
        this.refreshing.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not refresh the dashboard.'));
        this.refreshing.set(false);
      },
    });
  }

  ring(): string {
    const deg = this.creditRatio() * 360;
    return `conic-gradient(var(--accent) 0deg ${deg}deg, var(--line) ${deg}deg 360deg)`;
  }

  gradedPct(c: { graded: number; submitted: number }): number {
    return c.submitted ? Math.round((c.graded / c.submitted) * 100) : 0;
  }
}
