import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { CourseEnrollmentResponse, CourseEnrollmentStatus, MyStudentResponse } from '../../core/models/enrollment.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorMessage, initials, saveBlob, Toaster } from '../../shared/ui';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, paginate, showingSummary } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 50;
/** A single bounded fetch covering a whole offering's roster (capacity-bound, backend
 * max 200) — used only for stat tiles, filter-dropdown options and CSV export, which all
 * need the full roster rather than just the currently-displayed page. */
const FULL_ROSTER_SIZE = 200;

/**
 * Wireframe lecturerStudents + lecturerRoster in one screen. "All my courses" lists every
 * student across the lecturer's offerings; choosing one course shows that offering's
 * roster — enrolled and waitlisted — with capacity and core/elective counts.
 */
@Component({
  selector: 'app-students',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent, SearchInputComponent, RowMenuComponent, FilterBarComponent, PagerComponent],
  templateUrl: './students.component.html',
})
export class StudentsComponent {
  private enrollment = inject(EnrollmentService);
  private academic = inject(AcademicStructureService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly auth = inject(AuthService);
  private loadRequestId = 0;

  readonly offerings = signal<CourseOfferingResponse[]>([]);
  readonly offeringId = signal<string>(this.route.snapshot.queryParamMap.get('offering') ?? '');
  readonly allStudents = signal<MyStudentResponse[]>([]);
  /** The roster table's current, server-paginated page. */
  readonly roster = signal<CourseEnrollmentResponse[]>([]);
  /** The whole offering's roster (every status, one bounded fetch) — stat tiles, the
   * Program/Year filter options and CSV export all need the full set, not just a page. */
  readonly rosterAll = signal<CourseEnrollmentResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly search = signal('');
  readonly programFilter = signal('');
  readonly yearFilter = signal('');
  readonly statusFilter = signal('');
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));
  readonly initials = initials;

  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  readonly selectedOffering = computed(() => this.offerings().find((o) => o.id === this.offeringId()) ?? null);
  readonly programs = computed(() => {
    const names = this.offeringId()
      ? this.rosterAll().map((r) => r.studentProgramName)
      : this.allStudents().map((s) => s.programName);
    return [...new Set(names.filter((n): n is string => !!n))];
  });
  readonly offeringOptions = computed<SearchableSelectOption[]>(() =>
    this.offerings().map((o) => ({ value: o.id, label: `${o.code} — ${o.name}`, sublabel: `${o.academicYearName}${o.semesterName ? ' · ' + o.semesterName : ''}` })),
  );
  readonly programOptions = computed<SearchableSelectOption[]>(() =>
    [...this.programs()].sort().map((p) => ({ value: p, label: p })),
  );
  readonly yearOptions = computed<SearchableSelectOption[]>(() => {
    const years = this.offeringId()
      ? this.rosterAll().map((r) => r.studentYearOfStudy)
      : this.allStudents().map((s) => s.yearOfStudy);
    return [...new Set(years.filter((n): n is number => typeof n === 'number'))]
      .sort((a, b) => a - b)
      .map((n) => ({ value: String(n), label: `Year ${n}` }));
  });
  readonly statusOptions: SearchableSelectOption[] = [
    { value: 'ENROLLED', label: 'Enrolled' },
    { value: 'WAITLISTED', label: 'Waitlisted' },
  ];
  readonly activeFilterCount = computed(() =>
    countActive(this.offeringId(), this.programFilter(), this.yearFilter(), this.statusFilter(), this.search().trim()),
  );
  readonly filterSummary = computed(() =>
    this.offeringId()
      ? showingSummary(this.filteredRosterAll().length, this.rosterAll().length, 'student')
      : showingSummary(this.filteredAll().length, this.allStudents().length, 'student'),
  );

  readonly filteredAll = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.allStudents().filter(
      (s) =>
        (!this.programFilter() || s.programName === this.programFilter()) &&
        (!this.yearFilter() || String(s.yearOfStudy) === this.yearFilter()) &&
        (!q || (s.name ?? '').toLowerCase().includes(q) || (s.studentNumber ?? '').toLowerCase().includes(q)),
    );
  });
  /** "All my courses" has no server pagination (myStudents() is a cross-offering
   * aggregate, not a single paginated query) — paginate the already-filtered list
   * client-side instead, same convention as the Learning Hub's own scoped lists. */
  readonly pagedAll = computed(() => paginate(this.filteredAll(), this.page(), this.pageSize()));
  readonly allTotalPages = computed(() => Math.max(1, Math.ceil(this.filteredAll().length / this.pageSize())));
  /** The table's current page, further narrowed by Program/Year/search — these three
   * filters can't be pushed to the server (they're cross-service enrichment fields, not
   * native CourseEnrollment columns), so they apply only within the loaded page; Status
   * IS a native field and is sent to the server instead, so it's not re-applied here. */
  readonly filteredRoster = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.roster().filter(
      (r) =>
        (!this.programFilter() || r.studentProgramName === this.programFilter()) &&
        (!this.yearFilter() || String(r.studentYearOfStudy) === this.yearFilter()) &&
        (!q || (r.studentName ?? '').toLowerCase().includes(q) || (r.studentNumber ?? '').toLowerCase().includes(q)),
    );
  });
  /** Same filters as filteredRoster, but over the whole roster — used where a page-local
   * count or export would otherwise be misleadingly incomplete. */
  readonly filteredRosterAll = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.rosterAll().filter(
      (r) =>
        (!this.programFilter() || r.studentProgramName === this.programFilter()) &&
        (!this.yearFilter() || String(r.studentYearOfStudy) === this.yearFilter()) &&
        (!this.statusFilter() || r.status === this.statusFilter()) &&
        (!q || (r.studentName ?? '').toLowerCase().includes(q) || (r.studentNumber ?? '').toLowerCase().includes(q)),
    );
  });
  readonly enrolledCount = computed(() => this.rosterAll().filter((r) => r.status === 'ENROLLED').length);
  readonly waitlistCount = computed(() => this.rosterAll().filter((r) => r.status === 'WAITLISTED').length);
  readonly coreCount = computed(() => this.rosterAll().filter((r) => r.status === 'ENROLLED' && r.requirementType === 'CORE').length);

  constructor() {
    const isAdmin = this.auth.currentUser()?.role === 'ADMIN';
    this.academic.listCourseOfferings({ mine: !isAdmin }).subscribe({
      next: (v) => this.offerings.set(v.filter((o) => o.academicYearStatus !== 'ARCHIVED')),
    });
    this.load();
  }

  selectOffering(id: string): void {
    this.offeringId.set(id);
    this.programFilter.set('');
    this.yearFilter.set('');
    this.statusFilter.set('');
    this.page.set(0);
    this.router.navigate([], { queryParams: { offering: id || null }, replaceUrl: true });
    this.load();
  }

  onStatusFilterChange(value: string): void {
    this.statusFilter.set(value);
    this.page.set(0);
    this.load();
  }

  onSearchChange(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }

  onProgramFilterChange(value: string): void {
    this.programFilter.set(value);
    this.page.set(0);
  }

  onYearFilterChange(value: string): void {
    this.yearFilter.set(value);
    this.page.set(0);
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    if (this.offeringId()) this.load();
  }

  goToPage(page: number): void {
    this.page.set(page);
    if (this.offeringId()) this.load();
  }

  clearFilters(): void {
    this.search.set('');
    if (this.offeringId()) this.selectOffering('');
    else {
      this.programFilter.set('');
      this.yearFilter.set('');
      this.statusFilter.set('');
    }
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const id = this.offeringId();
    if (id) {
      const requestId = ++this.loadRequestId;
      this.enrollment.roster(id, { status: (this.statusFilter() as CourseEnrollmentStatus) || '', page: this.page(), size: this.pageSize() }).subscribe({
        next: (p) => {
          if (requestId !== this.loadRequestId) return; // a newer filter/page change already superseded this request
          this.roster.set(p.content);
          this.totalElements.set(p.totalElements);
          this.totalPages.set(p.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          if (requestId !== this.loadRequestId) return;
          this.loadError.set(errorMessage(err, 'Could not load this roster.'));
          this.loading.set(false);
        },
      });
      this.enrollment.roster(id, { size: FULL_ROSTER_SIZE }).subscribe({ next: (p) => this.rosterAll.set(p.content) });
    } else {
      this.rosterAll.set([]);
      this.enrollment.myStudents().subscribe({
        next: (v) => {
          this.allStudents.set(v);
          this.loading.set(false);
        },
        error: (err) => {
          this.loadError.set(errorMessage(err, 'Could not load your students.'));
          this.loading.set(false);
        },
      });
    }
  }

  remove(row: CourseEnrollmentResponse): void {
    if (!confirm(`Remove ${row.studentName} from ${row.courseCode}?`)) return;
    this.enrollment.drop(row.id).subscribe({
      next: () => {
        this.toaster.show(`${row.studentName} removed from ${row.courseCode}.`);
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not remove this student.'), true),
    });
  }

  exportCsv(): void {
    const header = ['Student', 'Student ID', 'Email', 'Program', 'Year', 'Enrolled On', 'Requirement', 'Status'];
    const rows = this.filteredRosterAll().map((r) => [
      r.studentName ?? '', r.studentNumber ?? '', r.studentEmail ?? '', r.studentProgramName ?? '',
      r.studentYearOfStudy?.toString() ?? '', r.enrolledOn, r.requirementType ?? '', r.status,
    ]);
    const cell = (c: string) => {
      const safe = /^[=+\-@]/.test(c) ? `'${c}` : c;
      return `"${safe.replace(/"/g, '""')}"`;
    };
    const csv = [header, ...rows].map((cols) => cols.map(cell).join(',')).join('\r\n');
    saveBlob(new Blob(['﻿' + csv], { type: 'text/csv' }), `${this.selectedOffering()?.code ?? 'roster'}-roster.csv`);
  }
}
