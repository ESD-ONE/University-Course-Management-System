import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AssignmentResponse, SubmissionRow } from '../../core/models/grading.model';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage, initials, saveBlob, Toaster } from '../../shared/ui';

interface EditableRow extends SubmissionRow {
  draftMarks: number | null;
  draftFeedback: string;
}

/**
 * Wireframe lecturerGrading / flow-02-grading: every enrolled student for the chosen
 * assignment (including non-submitters), marks entry, and "Save All Grades" — each saved
 * grade notifies the student and updates the class average on the dashboards.
 */
@Component({
  selector: 'app-grading',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './grading.component.html',
})
export class GradingComponent {
  private grading = inject(GradingService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly assignmentId = signal<string>(this.route.snapshot.queryParamMap.get('assignment') ?? '');
  readonly rows = signal<EditableRow[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));
  readonly initials = initials;

  readonly assignment = computed(() => this.assignments().find((a) => a.id === this.assignmentId()) ?? null);
  readonly submitted = computed(() => this.rows().filter((r) => r.status !== 'NOT_SUBMITTED').length);
  readonly graded = computed(() => this.rows().filter((r) => r.status === 'GRADED').length);
  readonly average = computed(() => {
    const max = this.assignment()?.maxMarks ?? 0;
    const marks = this.rows().filter((r) => r.marks !== null).map((r) => r.marks as number);
    return marks.length && max ? Math.round((marks.reduce((a, b) => a + b, 0) / marks.length / max) * 1000) / 10 : null;
  });
  readonly dirty = computed(() =>
    this.rows().filter(
      (r) =>
        r.submissionId &&
        r.draftMarks !== null &&
        (r.draftMarks !== r.marks || (r.draftFeedback || '') !== (r.feedback || '')),
    ),
  );

  constructor() {
    this.grading.list().subscribe({
      next: (v) => {
        const gradable = v.filter((a) => a.status !== 'DRAFT');
        this.assignments.set(gradable);
        if (!this.assignmentId() && gradable.length) this.assignmentId.set(gradable[0].id);
        if (this.assignmentId()) this.load();
      },
      error: (err) => this.loadError.set(errorMessage(err, 'Could not load assignments.')),
    });
  }

  select(id: string): void {
    this.assignmentId.set(id);
    this.router.navigate([], { queryParams: { assignment: id }, replaceUrl: true });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.grading.submissions(this.assignmentId()).subscribe({
      next: (v) => {
        this.rows.set(v.map((r) => ({ ...r, draftMarks: r.marks, draftFeedback: r.feedback ?? '' })));
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load submissions.'));
        this.loading.set(false);
      },
    });
  }

  setMarks(row: EditableRow, value: string): void {
    const n = value === '' ? null : Number(value);
    this.rows.update((rows) => rows.map((r) => (r === row ? { ...r, draftMarks: n } : r)));
  }

  setFeedback(row: EditableRow, value: string): void {
    this.rows.update((rows) => rows.map((r) => (r === row ? { ...r, draftFeedback: value } : r)));
  }

  saveAll(): void {
    const max = this.assignment()?.maxMarks ?? 0;
    const invalid = this.dirty().find((r) => (r.draftMarks as number) < 0 || (r.draftMarks as number) > max);
    if (invalid) {
      this.toaster.show(`Marks for ${invalid.studentName} must be between 0 and ${max}.`, true);
      return;
    }
    const grades = this.dirty().map((r) => ({
      submissionId: r.submissionId as string,
      marks: r.draftMarks as number,
      feedback: r.draftFeedback || null,
    }));
    if (!grades.length) return;
    this.saving.set(true);
    this.grading.gradeAll(this.assignmentId(), grades).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.toaster.show(`${saved.length} grade${saved.length === 1 ? '' : 's'} saved — students notified.`);
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.toaster.show(errorMessage(err, 'Could not save grades.'), true);
      },
    });
  }

  download(row: EditableRow): void {
    if (!row.submissionId) return;
    this.grading.downloadSubmission(row.submissionId).subscribe({
      next: (blob) => saveBlob(blob, row.fileName ?? 'submission'),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not download this file.'), true),
    });
  }
}
