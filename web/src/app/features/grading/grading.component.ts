import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AssignmentAnalyticsResponse, AssignmentResponse, SubmissionRow } from '../../core/models/grading.model';
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

  readonly showAnalytics = signal(false);
  readonly analytics = signal<AssignmentAnalyticsResponse | null>(null);
  readonly analyticsLoading = signal(false);
  readonly visibilityBusy = signal(false);

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
    // Every gradable assignment across the lecturer's courses — a bounded set, so
    // request a page large enough to cover it in one go rather than the default size.
    this.grading.list({ size: 100 }).subscribe({
      next: (p) => {
        const gradable = p.content.filter((a) => a.status !== 'DRAFT');
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
    this.showAnalytics.set(false);
    this.analytics.set(null);
    this.load();
  }

  toggleAnalytics(): void {
    this.showAnalytics.update((v) => !v);
    if (this.showAnalytics() && !this.analytics()) {
      this.analyticsLoading.set(true);
      this.grading.assignmentAnalytics(this.assignmentId()).subscribe({
        next: (a) => {
          this.analytics.set(a);
          this.analyticsLoading.set(false);
        },
        error: (err) => {
          this.analyticsLoading.set(false);
          this.toaster.show(errorMessage(err, 'Could not load analytics.'), true);
        },
      });
    }
  }

  toggleGradesHidden(): void {
    const a = this.assignment();
    if (!a) return;
    this.visibilityBusy.set(true);
    const onDone = () => {
      this.visibilityBusy.set(false);
      this.toaster.show(a.gradesHidden ? 'Grades released — students can now see them.' : 'Grades hidden until you release them.');
      this.load();
      this.refreshAssignment();
    };
    const onError = (err: unknown) => {
      this.visibilityBusy.set(false);
      this.toaster.show(errorMessage(err, 'Could not change grade visibility.'), true);
    };
    if (a.gradesHidden) {
      this.grading.releaseGrades(a.id).subscribe({ next: onDone, error: onError });
    } else {
      this.grading.setGradesHidden(a.id, true).subscribe({ next: onDone, error: onError });
    }
  }

  private refreshAssignment(): void {
    this.grading.list({ size: 100 }).subscribe({ next: (p) => this.assignments.set(p.content.filter((a) => a.status !== 'DRAFT')) });
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
