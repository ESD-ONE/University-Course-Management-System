import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AssignmentResponse } from '../../core/models/grading.model';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage } from '../../shared/ui';
import { AssignmentDetailComponent } from './assignment-detail.component';

/** Wireframe studentAssignment: published assignments in the student's courses; pick
 *  one to read the instructions, open the brief, and submit (or replace before the
 *  deadline). The detail panel is shared with the course workspace. */
@Component({
  selector: 'app-my-assignments',
  standalone: true,
  imports: [CommonModule, AssignmentDetailComponent],
  templateUrl: './my-assignments.component.html',
})
export class MyAssignmentsComponent {
  private grading = inject(GradingService);

  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly selected = computed(() => this.assignments().find((a) => a.id === this.selectedId()) ?? null);

  constructor() {
    this.load();
  }

  load(): void {
    // A student's own assignments across their courses — a bounded set, so request a
    // page large enough to cover it in one go rather than the default page size.
    this.grading.list({ size: 100 }).subscribe({
      next: (p) => {
        const v = [...p.content].sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime());
        this.assignments.set(v);
        if (!this.selectedId() && v.length) {
          const next = v.find((a) => a.status === 'OPEN' && a.mySubmissionStatus === 'NOT_SUBMITTED') ?? v[0];
          this.selectedId.set(next.id);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load your assignments.'));
        this.loading.set(false);
      },
    });
  }

  statusLabel(a: AssignmentResponse): string {
    return a.mySubmissionStatus === 'GRADED' ? 'Graded' : a.mySubmissionStatus === 'SUBMITTED' ? 'Submitted' : 'To do';
  }
}
