import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { CourseGradeRow, GradeHistoryResponse } from '../../core/models/grading.model';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage } from '../../shared/ui';

/** Wireframe studentGrades: grade history grouped by semester, plus cumulative GPA.
 *  A course's grade is its percentage over graded work so far. */
@Component({
  selector: 'app-grades',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './grades.component.html',
})
export class GradesComponent {
  private grading = inject(GradingService);

  readonly history = signal<GradeHistoryResponse | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly bySemester = computed(() => {
    const groups = new Map<string, CourseGradeRow[]>();
    for (const c of this.history()?.courses ?? []) {
      const key = `${c.semesterName ? c.semesterName + ' · ' : ''}${c.academicYearName ?? ''}`;
      groups.set(key, [...(groups.get(key) ?? []), c]);
    }
    return [...groups.entries()].map(([label, rows]) => ({ label, rows }));
  });

  constructor() {
    this.grading.myGrades().subscribe({
      next: (v) => {
        this.history.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load your grades.'));
        this.loading.set(false);
      },
    });
  }
}
