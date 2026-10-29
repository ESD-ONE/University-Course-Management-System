import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AssignmentResponse } from '../../core/models/grading.model';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage, saveBlob, Toaster } from '../../shared/ui';

const MAX_BYTES = 20 * 1024 * 1024;

/** Wireframe studentAssignment: published assignments in the student's courses; pick
 *  one to read the instructions and submit (or replace before the deadline). */
@Component({
  selector: 'app-my-assignments',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './my-assignments.component.html',
})
export class MyAssignmentsComponent {
  private grading = inject(GradingService);

  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly file = signal<File | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly selected = computed(() => this.assignments().find((a) => a.id === this.selectedId()) ?? null);
  readonly pastDue = computed(() => {
    const a = this.selected();
    return !!a && new Date(a.dueDate).getTime() < Date.now();
  });
  readonly canSubmit = computed(() => {
    const a = this.selected();
    return !!a && a.status === 'OPEN' && !this.pastDue() && a.mySubmissionStatus !== 'GRADED';
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.grading.list().subscribe({
      next: (v) => {
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

  dueLabel(a: AssignmentResponse): string {
    const ms = new Date(a.dueDate).getTime() - Date.now();
    if (ms < 0) return 'Past due';
    const days = Math.floor(ms / 86400000);
    if (days === 0) return 'Due today';
    return days === 1 ? 'Due tomorrow' : `Due in ${days} days`;
  }

  pick(event: Event): void {
    const f = (event.target as HTMLInputElement).files?.[0] ?? null;
    if (f && f.size > MAX_BYTES) {
      this.toaster.show('That file is larger than 20MB.', true);
      return;
    }
    this.file.set(f);
  }

  submit(): void {
    const a = this.selected();
    const f = this.file();
    if (!a || !f) return;
    this.uploading.set(true);
    this.grading.submit(a.id, f).subscribe({
      next: () => {
        this.uploading.set(false);
        this.file.set(null);
        this.toaster.show(`Submitted "${f.name}" for ${a.title}.`);
        this.load();
      },
      error: (err) => {
        this.uploading.set(false);
        this.toaster.show(errorMessage(err, 'Could not submit your file.'), true);
      },
    });
  }

  downloadBrief(a: AssignmentResponse): void {
    this.grading.downloadBrief(a.id).subscribe({
      next: (blob) => saveBlob(blob, a.briefFileName ?? 'brief'),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not download the brief.'), true),
    });
  }
}
