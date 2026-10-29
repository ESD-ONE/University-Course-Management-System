import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { ProgramCourseResponse } from '../../core/models/academic-structure.model';
import { CourseEnrollmentResponse, ProgramEnrollmentResponse } from '../../core/models/enrollment.model';
import { CourseGradeRow } from '../../core/models/grading.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage } from '../../shared/ui';

type Progress = 'completed' | 'in-progress' | 'not-started';

/**
 * Student "My Program" (wireframe studentProgram): the full curriculum of the student's
 * active degree program, each course marked Completed (a passing grade in a finished —
 * archived — academic year), In Progress (currently enrolled) or Not Started.
 */
@Component({
  selector: 'app-my-program',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './my-program.component.html',
})
export class MyProgramComponent {
  private enrollment = inject(EnrollmentService);
  private academic = inject(AcademicStructureService);
  private grading = inject(GradingService);

  readonly program = signal<ProgramEnrollmentResponse | null>(null);
  readonly curriculum = signal<ProgramCourseResponse[]>([]);
  readonly enrollments = signal<CourseEnrollmentResponse[]>([]);
  readonly grades = signal<CourseGradeRow[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly rows = computed(() =>
    this.curriculum().map((c) => ({ ...c, progress: this.progressOf(c.catalogCourseCode) })),
  );
  readonly creditsEarned = computed(() =>
    this.rows().filter((r) => r.progress === 'completed').reduce((a, r) => a + r.creditValue, 0),
  );
  readonly inProgress = computed(() => this.rows().filter((r) => r.progress === 'in-progress').length);
  readonly creditRatio = computed(() => {
    const total = this.program()?.totalCredits ?? 0;
    return total ? Math.min(1, this.creditsEarned() / total) : 0;
  });

  constructor() {
    this.enrollment
      .myProgramEnrollments()
      .pipe(
        switchMap((programs) => {
          const active = programs.find((p) => p.status === 'ACTIVE') ?? null;
          this.program.set(active);
          if (!active) return of(null);
          return forkJoin({
            curriculum: this.academic.listCurriculum(active.degreeProgramId),
            enrollments: this.enrollment.myEnrollments(),
            grades: this.grading.myGrades().pipe(catchError(() => of({ courses: [], gpa: null, creditsGraded: 0 }))),
          });
        }),
      )
      .subscribe({
        next: (data) => {
          if (data) {
            this.curriculum.set(data.curriculum);
            this.enrollments.set(data.enrollments);
            this.grades.set(data.grades.courses);
          }
          this.loading.set(false);
        },
        error: (err) => {
          this.loadError.set(errorMessage(err, 'Could not load your program.'));
          this.loading.set(false);
        },
      });
  }

  private progressOf(code: string): Progress {
    const passed = this.grades().some(
      (g) => g.courseCode === code && g.academicYearStatus === 'ARCHIVED' && (g.points ?? 0) > 0,
    );
    if (passed) return 'completed';
    if (this.enrollments().some((e) => e.courseCode === code && e.status === 'ENROLLED')) return 'in-progress';
    return 'not-started';
  }

  ring(): string {
    const deg = this.creditRatio() * 360;
    return `conic-gradient(var(--accent) 0deg ${deg}deg, var(--line) ${deg}deg 360deg)`;
  }
}
