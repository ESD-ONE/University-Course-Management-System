import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogEntry } from '../../core/models/enrollment.model';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorCode, errorMessage, Toaster } from '../../shared/ui';

/**
 * Student "Browse Courses" (wireframe studentBrowse): published offerings with live seat
 * counts. Enroll → if the course filled up meanwhile the backend answers 409 COURSE_FULL
 * and the student is offered the waitlist (flow-01's "Course is full — join waitlist?").
 */
@Component({
  selector: 'app-browse-courses',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './browse-courses.component.html',
})
export class BrowseCoursesComponent {
  private enrollment = inject(EnrollmentService);

  readonly entries = signal<CatalogEntry[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly busyId = signal<string | null>(null);
  readonly waitlistOffer = signal<CatalogEntry | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly yearFilter = signal('');
  readonly search = signal('');
  readonly availableOnly = signal(false);

  readonly years = computed(() => [...new Set(this.entries().map((e) => e.academicYearName))]);
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.entries().filter(
      (e) =>
        (!this.yearFilter() || e.academicYearName === this.yearFilter()) &&
        (!q || e.code.toLowerCase().includes(q) || e.name.toLowerCase().includes(q)) &&
        (!this.availableOnly() || (e.seatsLeft > 0 && e.openForEnrollment)),
    );
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.enrollment.catalog().subscribe({
      next: (v) => {
        this.entries.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load the course catalogue.'));
        this.loading.set(false);
      },
    });
  }

  enroll(entry: CatalogEntry, joinWaitlist = false): void {
    this.busyId.set(entry.offeringId);
    this.waitlistOffer.set(null);
    this.enrollment.enroll({ offeringId: entry.offeringId, joinWaitlistIfFull: joinWaitlist }).subscribe({
      next: (e) => {
        this.busyId.set(null);
        this.toaster.show(e.status === 'WAITLISTED' ? `You're on the waitlist for ${entry.code}.` : `Enrolled in ${entry.code}.`);
        this.load();
      },
      error: (err) => {
        this.busyId.set(null);
        if (errorCode(err) === 'COURSE_FULL') {
          this.waitlistOffer.set(entry);
          this.load();
        } else {
          this.toaster.show(errorMessage(err, `Could not enroll in ${entry.code}.`), true);
        }
      },
    });
  }
}
