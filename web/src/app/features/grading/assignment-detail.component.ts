import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AssignmentResponse } from '../../core/models/grading.model';
import { GradingService } from '../../core/services/grading.service';
import { canPreview, errorMessage, fileIcon, openBlob, saveBlob } from '../../shared/ui';

const MAX_BYTES = 20 * 1024 * 1024;

/**
 * A student's view of one assignment: instructions, the brief (preview or download),
 * their own submission (preview or download), upload/replace, and the released grade.
 * Shared by My Assignments and the course workspace so both behave the same.
 */
@Component({
  selector: 'app-assignment-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './assignment-detail.component.html',
})
export class AssignmentDetailComponent {
  private grading = inject(GradingService);

  private readonly a = signal<AssignmentResponse | null>(null);
  @Input({ required: true }) set assignment(value: AssignmentResponse) {
    // A reload after submitting hands back the same assignment — keep the confirmation.
    if (this.a()?.id !== value.id) {
      this.file.set(null);
      this.message.set(null);
    }
    this.a.set(value);
  }
  /** Shows an "Open course" link — off when already inside the course workspace. */
  @Input() showCourseLink = true;
  /** Emitted after a successful submission, so the parent can reload the list. */
  @Output() submitted = new EventEmitter<void>();

  readonly canPreview = canPreview;
  readonly fileIcon = fileIcon;
  readonly file = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly message = signal<{ text: string; error: boolean } | null>(null);

  readonly current = computed(() => this.a());
  readonly pastDue = computed(() => {
    const a = this.a();
    return !!a && new Date(a.dueDate).getTime() < Date.now();
  });
  readonly canSubmit = computed(() => {
    const a = this.a();
    return !!a && a.status === 'OPEN' && !this.pastDue() && a.mySubmissionStatus !== 'GRADED';
  });

  dueLabel(a: AssignmentResponse): string {
    const ms = new Date(a.dueDate).getTime() - Date.now();
    if (ms < 0) return 'Past due';
    const hours = ms / 3600000;
    if (hours < 24) return hours < 1 ? 'Due within the hour' : `Due in ${Math.floor(hours)}h`;
    const days = Math.floor(hours / 24);
    return days === 1 ? 'Due tomorrow' : `Due in ${days} days`;
  }

  statusLabel(a: AssignmentResponse): string {
    return a.mySubmissionStatus === 'GRADED' ? 'Graded' : a.mySubmissionStatus === 'SUBMITTED' ? 'Submitted' : 'Not submitted';
  }

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    input.value = '';
    if (f && f.size > MAX_BYTES) {
      this.message.set({ text: 'That file is larger than 20MB.', error: true });
      return;
    }
    this.message.set(null);
    this.file.set(f);
  }

  submit(): void {
    const a = this.a();
    const f = this.file();
    if (!a || !f) return;
    this.uploading.set(true);
    this.grading.submit(a.id, f).subscribe({
      next: () => {
        this.uploading.set(false);
        this.file.set(null);
        this.message.set({ text: `Submitted "${f.name}".`, error: false });
        this.submitted.emit();
      },
      error: (err) => {
        this.uploading.set(false);
        this.message.set({ text: errorMessage(err, 'Could not submit your file.'), error: true });
      },
    });
  }

  brief(a: AssignmentResponse, mode: 'view' | 'download'): void {
    const name = a.briefFileName ?? 'brief';
    this.grading.downloadBrief(a.id).subscribe({
      next: (blob) => (mode === 'view' ? openBlob(blob, name) : saveBlob(blob, name)),
      error: (err) => this.message.set({ text: errorMessage(err, 'Could not open the brief.'), error: true }),
    });
  }

  /** The list response doesn't carry the submission id, so look it up first. */
  mySubmission(a: AssignmentResponse, mode: 'view' | 'download'): void {
    this.grading.mySubmission(a.id).subscribe({
      next: (s) => {
        this.grading.downloadSubmission(s.submissionId).subscribe({
          next: (blob) => (mode === 'view' ? openBlob(blob, s.fileName) : saveBlob(blob, s.fileName)),
          error: (err) => this.message.set({ text: errorMessage(err, 'Could not open your file.'), error: true }),
        });
      },
      error: (err) => this.message.set({ text: errorMessage(err, 'Could not find your submission.'), error: true }),
    });
  }
}
