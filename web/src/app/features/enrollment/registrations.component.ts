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

/**
 * Wireframe adminRegistrations: register a student into a degree program (admin only)
 * or into a course on their behalf (admin: any published offering; lecturer: offerings
 * they teach). Same Enrollment write path as self-service; the registration log records
 * who acted (flow-03).
 */
@Component({
  selector: 'app-registrations',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  readonly studentId = signal('');
  readonly selectedStudent = computed(() => this.students().find((s) => s.id === this.studentId()) ?? null);
  private search$ = new Subject<string>();

  readonly programs = signal<DegreeProgramResponse[]>([]);
  readonly years = signal<AcademicYearResponse[]>([]);
  readonly offerings = signal<CourseOfferingResponse[]>([]);
  readonly programId = signal('');
  readonly intakeYearId = signal('');
  readonly yearOfStudy = signal(1);
  readonly offeringId = signal('');

  readonly durationOptions = computed(() => {
    const p = this.programs().find((x) => x.id === this.programId());
    return Array.from({ length: p?.durationYears ?? 4 }, (_, i) => i + 1);
  });
  /** Offerings from the student's own program first, then everything else. */
  readonly groupedOfferings = computed(() => {
    const programId = this.selectedStudent()?.activeProgramId;
    const all = this.offerings();
    return {
      program: programId ? all.filter((o) => o.degreeProgramId === programId) : [],
      other: programId ? all.filter((o) => o.degreeProgramId !== programId) : all,
    };
  });

  readonly recent = signal<RegistrationResponse[]>([]);
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
      .subscribe({ next: (v) => this.students.set(v), error: () => this.students.set([]) });
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

  onSearch(q: string): void {
    this.studentQuery.set(q);
    this.search$.next(q);
  }

  loadRecent(): void {
    this.enrollment.registrations(20).subscribe({ next: (v) => this.recent.set(v) });
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
          this.enrollment.searchStudents(this.studentQuery()).subscribe({ next: (v) => this.students.set(v) });
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
