import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { AcademicYearResponse, CourseOfferingResponse, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import { REGISTRATION_ACTION_LABELS, RegistrationResponse, StudentOption } from '../../core/models/enrollment.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorCode, errorMessage, initials, Toaster } from '../../shared/ui';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { countActive, matchesText, plural } from '../../shared/filters';

/** A roster fetch bounded by a typical offering's whole capacity — enough to find one
 * student's row without a dedicated by-student lookup endpoint. */
const FULL_ROSTER_SIZE = 200;

/**
 * Wireframe adminRegistrations: register a student into a degree program (admin only)
 * or into a course on their behalf (admin: any published offering; lecturer: offerings
 * they teach). Same Enrollment write path as self-service; the registration log records
 * who acted (flow-03).
 */
@Component({
  selector: 'app-registrations',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent, SearchInputComponent, FilterBarComponent, PagerComponent, RowMenuComponent],
  templateUrl: './registrations.component.html',
})
export class RegistrationsComponent {
  private enrollment = inject(EnrollmentService);
  private academic = inject(AcademicStructureService);
  readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly tab = signal<'program' | 'course'>(this.auth.currentUser()?.role === 'ADMIN' ? 'program' : 'course');

  readonly studentQuery = signal('');
  readonly students = signal<StudentOption[]>([]);
  readonly studentsLoading = signal(false);
  readonly studentId = signal('');
  /** Kept separately from the search results: a new search must not "lose" the
   * student already picked. */
  readonly selectedStudent = signal<StudentOption | null>(null);
  private search$ = new Subject<string>();

  readonly studentOptions = computed<SearchableSelectOption[]>(() => {
    const list = [...this.students()];
    const sel = this.selectedStudent();
    if (sel && !list.some((s) => s.id === sel.id)) list.unshift(sel);
    return list.map((s) => ({
      value: s.id,
      label: s.name,
      sublabel: [s.studentNumber ?? 'No student ID yet', s.email, s.activeProgramName].filter(Boolean).join(' · '),
    }));
  });

  readonly programs = signal<DegreeProgramResponse[]>([]);
  readonly years = signal<AcademicYearResponse[]>([]);
  readonly offerings = signal<CourseOfferingResponse[]>([]);
  readonly programId = signal('');
  readonly intakeYearId = signal('');
  readonly yearOfStudy = signal(1);
  readonly offeringId = signal('');

  readonly selectedProgram = computed(() => this.programs().find((x) => x.id === this.programId()) ?? null);
  readonly selectedYear = computed(() => this.years().find((y) => y.id === this.intakeYearId()) ?? null);
  readonly selectedOffering = computed(() => this.offerings().find((o) => o.id === this.offeringId()) ?? null);

  readonly programOptions = computed<SearchableSelectOption[]>(() =>
    this.programs().map((p) => ({ value: p.id, label: p.name, sublabel: `${p.code} · ${p.durationYears} years` })),
  );
  readonly yearOptions = computed<SearchableSelectOption[]>(() =>
    this.years().map((y) => ({ value: y.id, label: y.name, sublabel: y.status === 'ACTIVE' ? 'Active' : y.status.charAt(0) + y.status.slice(1).toLowerCase() })),
  );
  readonly yearOfStudyOptions = computed<SearchableSelectOption[]>(() =>
    Array.from({ length: this.selectedProgram()?.durationYears ?? 4 }, (_, i) => ({ value: String(i + 1), label: `Year ${i + 1}` })),
  );
  /** Offerings from the student's own program first, then everything else. */
  readonly groupedOfferings = computed(() => {
    const programId = this.selectedStudent()?.activeProgramId;
    const all = this.offerings();
    return {
      program: programId ? all.filter((o) => o.degreeProgramId === programId) : [],
      other: programId ? all.filter((o) => o.degreeProgramId !== programId) : all,
    };
  });

  /** The course list for the typing dropdown — the student's own program first. */
  readonly offeringOptions = computed<SearchableSelectOption[]>(() => {
    const { program, other } = this.groupedOfferings();
    const label = (o: CourseOfferingResponse) => `${o.code} — ${o.name}`;
    const when = (o: CourseOfferingResponse) => `${o.academicYearName}${o.semesterName ? ', ' + o.semesterName : ''}`;
    return [
      ...program.map((o) => ({ value: o.id, label: label(o), sublabel: `★ Student's program · Year ${o.yearOfStudy} · ${when(o)}` })),
      ...other.map((o) => ({ value: o.id, label: label(o), sublabel: `${o.degreeProgramCode} · Year ${o.yearOfStudy} · ${when(o)}` })),
    ];
  });

  readonly programBlocked = computed(() => !!this.selectedStudent()?.activeProgramName);
  readonly canSubmit = computed(() => {
    if (!this.studentId() || this.submitting()) return false;
    return this.tab() === 'program'
      ? !!this.programId() && !!this.intakeYearId() && !this.programBlocked()
      : !!this.offeringId();
  });

  /** Collapsed by default — the enrichment this endpoint does (cross-service name/label
   * lookups) only runs once an admin actually asks to see it. */
  readonly recentExpanded = signal(false);
  private recentLoadedOnce = false;
  readonly recentPageSize = signal(20);
  readonly recent = signal<RegistrationResponse[]>([]);
  readonly recentPage = signal(0);
  readonly recentTotalElements = signal(0);
  readonly recentTotalPages = signal(0);
  readonly recentLoading = signal(false);
  readonly recentSearch = signal('');
  readonly recentType = signal<'PROGRAM' | 'COURSE' | ''>('');
  readonly recentAction = signal('');
  readonly recentTypeOptions: SearchableSelectOption[] = [
    { value: 'PROGRAM', label: 'Program' },
    { value: 'COURSE', label: 'Course' },
  ];
  readonly recentActionOptions: SearchableSelectOption[] = Object.entries(REGISTRATION_ACTION_LABELS).map(([value, label]) => ({ value, label }));
  /** Server already applies targetType/action; free-text search can't reach the
   * cross-service names/labels server-side, so it narrows within the loaded page only. */
  readonly filteredRecent = computed(() =>
    this.recent().filter((r) => matchesText(this.recentSearch(), r.studentName, r.studentNumber, r.targetLabel, r.actorName)),
  );
  readonly recentFilterCount = computed(() => countActive(this.recentSearch().trim(), this.recentType(), this.recentAction()));
  readonly recentSummary = computed(() => {
    const total = plural(this.recentTotalElements(), 'registration');
    return this.recentType() || this.recentAction() ? `${total} match` : `${total} in total`;
  });
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly waitlistOffer = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));
  readonly initials = initials;
  readonly actionLabel = (a: string) => REGISTRATION_ACTION_LABELS[a] ?? a;

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), switchMap((q) => this.enrollment.searchStudents(q)))
      .subscribe({
        next: (v) => {
          this.students.set(v);
          this.studentsLoading.set(false);
        },
        error: () => {
          this.students.set([]);
          this.studentsLoading.set(false);
        },
      });
    this.search$.next('');
    this.academic.listAcademicYears().subscribe({
      next: (v) => {
        const open = v.filter((y) => y.status !== 'ARCHIVED');
        this.years.set(open);
        if (open.length) this.intakeYearId.set(open[0].id);
      },
    });
    if (this.isAdmin()) {
      this.academic.listDegreePrograms({ status: 'ACTIVE', size: 200 }).subscribe({
        next: (p) => {
          this.programs.set(p.content);
          if (p.content.length) this.programId.set(p.content[0].id);
        },
      });
    }
    this.academic.listCourseOfferings({ mine: !this.isAdmin(), status: 'PUBLISHED' }).subscribe({
      next: (v) => this.offerings.set(v.filter((o) => o.academicYearStatus !== 'ARCHIVED')),
    });
  }

  onStudentQuery(q: string): void {
    this.studentQuery.set(q);
    this.studentsLoading.set(true);
    this.search$.next(q.trim());
  }

  onStudentChange(id: string): void {
    this.studentId.set(id);
    this.selectedStudent.set(this.students().find((s) => s.id === id) ?? null);
    this.formError.set(null);
    this.waitlistOffer.set(false);
    this.offeringId.set('');
  }

  setTab(tab: 'program' | 'course'): void {
    this.tab.set(tab);
    this.formError.set(null);
    this.waitlistOffer.set(false);
  }

  onProgramChange(id: string): void {
    this.programId.set(id);
    this.yearOfStudy.set(1);
  }

  submit(): void {
    if (this.tab() === 'program') this.registerProgram();
    else this.registerCourse();
  }

  clearRecentFilters(): void {
    this.recentSearch.set('');
    this.recentType.set('');
    this.recentAction.set('');
    this.recentPage.set(0);
    if (this.recentExpanded()) this.loadRecent();
  }

  /** Toggles the collapsible "Recent Registrations" panel — loaded lazily, the first
   * time it's actually expanded, since the enrichment this endpoint does (cross-service
   * name/label lookups) isn't worth running until someone asks to see it. */
  toggleRecent(): void {
    this.recentExpanded.update((v) => !v);
    if (this.recentExpanded() && !this.recentLoadedOnce) {
      this.recentLoadedOnce = true;
      this.loadRecent();
    }
  }

  onRecentTypeChange(value: 'PROGRAM' | 'COURSE' | ''): void {
    this.recentType.set(value);
    this.recentPage.set(0);
    this.loadRecent();
  }

  onRecentActionChange(value: string): void {
    this.recentAction.set(value);
    this.recentPage.set(0);
    this.loadRecent();
  }

  goToRecentPage(page: number): void {
    this.recentPage.set(page);
    this.loadRecent();
  }

  onRecentPageSizeChange(size: number): void {
    this.recentPageSize.set(size);
    this.recentPage.set(0);
    this.loadRecent();
  }

  loadRecent(): void {
    this.recentLoading.set(true);
    this.enrollment
      .registrations({
        targetType: this.recentType() || undefined,
        action: this.recentAction() || undefined,
        page: this.recentPage(),
        size: this.recentPageSize(),
      })
      .subscribe({
        next: (p) => {
          this.recent.set(p.content);
          this.recentPage.set(p.number);
          this.recentTotalElements.set(p.totalElements);
          this.recentTotalPages.set(p.totalPages);
          this.recentLoading.set(false);
        },
        error: () => this.recentLoading.set(false),
      });
  }

  /** After a successful program registration: the student picked is now registered
   * (the program tab blocks re-registering them), so clear step 1 and the year-of-study
   * back to their defaults, ready for the next student — but keep the chosen program and
   * intake year, since registering a whole cohort into the same program is the common
   * case. */
  private resetAfterProgramRegistration(): void {
    this.studentId.set('');
    this.selectedStudent.set(null);
    this.studentQuery.set('');
    this.yearOfStudy.set(1);
    this.formError.set(null);
    this.search$.next('');
  }

  registerProgram(): void {
    if (!this.studentId() || !this.programId() || !this.intakeYearId()) return;
    this.submitting.set(true);
    this.formError.set(null);
    this.enrollment
      .registerProgram({
        studentId: this.studentId(),
        degreeProgramId: this.programId(),
        intakeAcademicYearId: this.intakeYearId(),
        currentYearOfStudy: this.yearOfStudy(),
      })
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          this.toaster.show(`${r.studentName} registered in ${r.degreeProgramName}.`);
          this.resetAfterProgramRegistration();
          if (this.recentExpanded()) this.loadRecent();
        },
        error: (err) => {
          this.submitting.set(false);
          this.formError.set(errorMessage(err, 'Could not register this student.'));
        },
      });
  }

  registerCourse(joinWaitlist = false): void {
    if (!this.studentId() || !this.offeringId()) return;
    this.submitting.set(true);
    this.formError.set(null);
    this.waitlistOffer.set(false);
    this.enrollment
      .enroll({ offeringId: this.offeringId(), studentId: this.studentId(), joinWaitlistIfFull: joinWaitlist })
      .subscribe({
        next: (e) => {
          this.submitting.set(false);
          this.toaster.show(
            e.status === 'WAITLISTED' ? `Added to the waitlist for ${e.courseCode}.` : `Registered in ${e.courseCode}.`,
          );
          this.offeringId.set('');
          if (this.recentExpanded()) this.loadRecent();
        },
        error: (err) => {
          this.submitting.set(false);
          if (errorCode(err) === 'COURSE_FULL') this.waitlistOffer.set(true);
          else this.formError.set(errorMessage(err, 'Could not register this student.'));
        },
      });
  }

  /** The "Recent Registrations" kebab's "Delete" action, confirmed through an in-app
   * dialog (see confirmTarget/confirmDelete) rather than the browser's native confirm().
   * A log row only tells us the student and the target (course offering / degree
   * program), not the enrollment's own id, so deleting first looks up the student's
   * current, still-active row for that target — ending it — before removing the log
   * entry itself. If no active row is found (already dropped/withdrawn since this log
   * entry was written), it skips straight to removing the log entry. */
  readonly confirmTarget = signal<RegistrationResponse | null>(null);
  readonly deleting = signal(false);

  requestDelete(r: RegistrationResponse): void {
    this.confirmTarget.set(r);
  }

  cancelDelete(): void {
    if (this.deleting()) return;
    this.confirmTarget.set(null);
  }

  confirmDelete(): void {
    const r = this.confirmTarget();
    if (!r || this.deleting()) return;
    this.deleting.set(true);

    const finish = (message: string, error = false) => {
      this.deleting.set(false);
      this.confirmTarget.set(null);
      this.toaster.show(message, error);
      this.loadRecent();
    };
    const deleteLogEntry = () => {
      this.enrollment.deleteRegistration(r.id).subscribe({
        next: () => finish(`Registration removed${r.studentName ? ' for ' + r.studentName : ''}.`),
        error: (err) => finish(errorMessage(err, 'Could not delete this registration.'), true),
      });
    };

    if (r.targetType === 'COURSE') {
      this.enrollment.roster(r.targetId, { size: FULL_ROSTER_SIZE }).subscribe({
        next: (page) => {
          const row = page.content.find((x) => x.studentId === r.studentId && (x.status === 'ENROLLED' || x.status === 'WAITLISTED'));
          if (!row) { deleteLogEntry(); return; }
          this.enrollment.drop(row.id).subscribe({
            next: deleteLogEntry,
            error: (err) => finish(errorMessage(err, 'Could not unenroll this student.'), true),
          });
        },
        error: (err) => finish(errorMessage(err, 'Could not look up this enrollment.'), true),
      });
    } else {
      this.enrollment.programEnrollments(r.studentId).subscribe({
        next: (list) => {
          const row = list.find((x) => x.degreeProgramId === r.targetId && (x.status === 'ACTIVE' || x.status === 'DEFERRED'));
          if (!row) { deleteLogEntry(); return; }
          this.enrollment.updateProgramEnrollment(row.id, { status: 'WITHDRAWN' }).subscribe({
            next: deleteLogEntry,
            error: (err) => finish(errorMessage(err, 'Could not withdraw this student.'), true),
          });
        },
        error: (err) => finish(errorMessage(err, 'Could not look up this registration.'), true),
      });
    }
  }
}
