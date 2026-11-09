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
import { countActive, matchesText, showingSummary } from '../../shared/filters';

/**
 * Wireframe adminRegistrations: register a student into a degree program (admin only)
 * or into a course on their behalf (admin: any published offering; lecturer: offerings
 * they teach). Same Enrollment write path as self-service; the registration log records
 * who acted (flow-03).
 */
@Component({
  selector: 'app-registrations',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent, SearchInputComponent, FilterBarComponent],
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

  readonly recent = signal<RegistrationResponse[]>([]);
  readonly recentLoading = signal(true);
  readonly recentSearch = signal('');
  readonly recentType = signal('');
  readonly recentAction = signal('');
  readonly recentTypeOptions: SearchableSelectOption[] = [
    { value: 'PROGRAM', label: 'Program' },
    { value: 'COURSE', label: 'Course' },
  ];
  readonly recentActionOptions: SearchableSelectOption[] = Object.entries(REGISTRATION_ACTION_LABELS).map(([value, label]) => ({ value, label }));
  readonly filteredRecent = computed(() =>
    this.recent().filter(
      (r) =>
        matchesText(this.recentSearch(), r.studentName, r.studentNumber, r.targetLabel, r.actorName) &&
        (!this.recentType() || r.targetType === this.recentType()) &&
        (!this.recentAction() || r.action === this.recentAction()),
    ),
  );
  readonly recentFilterCount = computed(() => countActive(this.recentSearch().trim(), this.recentType(), this.recentAction()));
  readonly recentSummary = computed(() => showingSummary(this.filteredRecent().length, this.recent().length, 'registration'));
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
      this.academic.listDegreePrograms().subscribe({
        next: (v) => {
          const active = v.filter((p) => p.status === 'ACTIVE');
          this.programs.set(active);
          if (active.length) this.programId.set(active[0].id);
        },
      });
    }
    this.academic.listCourseOfferings({ mine: !this.isAdmin(), status: 'PUBLISHED' }).subscribe({
      next: (v) => this.offerings.set(v.filter((o) => o.academicYearStatus !== 'ARCHIVED')),
    });
    this.loadRecent();
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
  }

  loadRecent(): void {
    this.enrollment.registrations(50).subscribe({
      next: (v) => {
        this.recent.set(v);
        this.recentLoading.set(false);
      },
      error: () => this.recentLoading.set(false),
    });
  }

  /** Re-read the picked student (e.g. their program changed after registering). */
  private refreshSelectedStudent(): void {
    const sel = this.selectedStudent();
    if (!sel) return;
    this.enrollment.searchStudents(sel.email || sel.name).subscribe({
      next: (v) => {
        this.students.set(v);
        this.selectedStudent.set(v.find((s) => s.id === sel.id) ?? sel);
      },
    });
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
          this.refreshSelectedStudent();
          this.loadRecent();
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
          this.loadRecent();
        },
        error: (err) => {
          this.submitting.set(false);
          if (errorCode(err) === 'COURSE_FULL') this.waitlistOffer.set(true);
          else this.formError.set(errorMessage(err, 'Could not register this student.'));
        },
      });
  }
}
