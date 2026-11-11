import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogEntry } from '../../core/models/enrollment.model';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorCode, errorMessage, Toaster } from '../../shared/ui';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, numberOptions, paginate, uniqueOptions } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 12;

/**
 * Student "Browse Courses" (wireframe studentBrowse): published offerings in the
 * student's own registered degree program — never every offering university-wide, see
 * EnrollmentService.catalog() — with live seat counts. Enroll → if the course filled up
 * meanwhile the backend answers 409 COURSE_FULL and the student is offered the
 * waitlist (flow-01's "Course is full — join waitlist?").
 */
@Component({
  selector: 'app-browse-courses',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent, SearchInputComponent, FilterBarComponent, PagerComponent],
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
  readonly yearOfStudyFilter = signal('');
  readonly yearOptions = computed(() => uniqueOptions(this.entries(), (e) => e.academicYearName, (e) => e.academicYearName));
  readonly yearOfStudyOptions = computed(() => numberOptions(this.entries().map((e) => e.yearOfStudy), (n) => `Year ${n}`));

  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.entries().filter(
      (e) =>
        (!this.yearFilter() || e.academicYearName === this.yearFilter()) &&
        (!this.yearOfStudyFilter() || String(e.yearOfStudy) === this.yearOfStudyFilter()) &&
        (!q || e.code.toLowerCase().includes(q) || e.name.toLowerCase().includes(q)) &&
        (!this.availableOnly() || (e.seatsLeft > 0 && e.openForEnrollment)),
    );
  });
  readonly pagedEntries = computed(() => paginate(this.filtered(), this.page(), this.pageSize()));
  readonly totalElements = computed(() => this.filtered().length);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / this.pageSize())));

  readonly activeFilterCount = computed(() =>
    countActive(this.yearFilter(), this.yearOfStudyFilter(), this.search().trim(), this.availableOnly()),
  );
  readonly filterSummary = computed(() => {
    if (this.loading() || this.loadError()) return '';
    return `${this.totalElements()} course${this.totalElements() === 1 ? '' : 's'}${this.activeFilterCount() ? ' match' : ' in total'}`;
  });

  onSearchChange(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }

  onYearFilterChange(value: string): void {
    this.yearFilter.set(value);
    this.page.set(0);
  }

  onYearOfStudyFilterChange(value: string): void {
    this.yearOfStudyFilter.set(value);
    this.page.set(0);
  }

  onAvailableOnlyChange(value: boolean): void {
    this.availableOnly.set(value);
    this.page.set(0);
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  clearFilters(): void {
    this.yearFilter.set('');
    this.yearOfStudyFilter.set('');
    this.search.set('');
    this.availableOnly.set(false);
    this.page.set(0);
  }

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
