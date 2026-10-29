import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CourseEnrollmentResponse } from '../../core/models/enrollment.model';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorMessage, Toaster } from '../../shared/ui';

/** Student "My Enrollments" (wireframe studentEnrollments): enrolled and waitlisted
 *  courses, with Drop. Dropping a seat promotes the next waitlisted student. */
@Component({
  selector: 'app-my-enrollments',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './my-enrollments.component.html',
})
export class MyEnrollmentsComponent {
  private enrollment = inject(EnrollmentService);

  readonly rows = signal<CourseEnrollmentResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly confirming = signal<CourseEnrollmentResponse | null>(null);
  readonly busy = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly enrolledCredits = computed(() =>
    this.rows().filter((r) => r.status === 'ENROLLED').reduce((a, r) => a + (r.credits ?? 0), 0),
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.enrollment.myEnrollments().subscribe({
      next: (v) => {
        this.rows.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load your enrollments.'));
        this.loading.set(false);
      },
    });
  }

  drop(row: CourseEnrollmentResponse): void {
    this.busy.set(true);
    this.enrollment.drop(row.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirming.set(null);
        this.toaster.show(`Dropped ${row.courseCode}.`);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.confirming.set(null);
        this.toaster.show(errorMessage(err, `Could not drop ${row.courseCode}.`), true);
      },
    });
  }
}
