import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, of, switchMap } from 'rxjs';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { WeekResponse } from '../../core/models/course-content.model';
import { AssignmentResponse } from '../../core/models/grading.model';
import { CourseContentService } from '../../core/services/course-content.service';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage, fileIcon } from '../../shared/ui';

const MAX_BYTES = 20 * 1024 * 1024;

/**
 * The one create/edit form for an assignment — used by the Assignments screen and by the
 * course workspace (where the course, and usually the week, are already decided). Saves
 * the assignment, then uploads the brief if one was chosen, so the lecturer does both in
 * a single step instead of saving and then finding "Upload brief…" in a row menu.
 */
@Component({
  selector: 'app-assignment-form-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './assignment-form-dialog.component.html',
})
export class AssignmentFormDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private grading = inject(GradingService);
  private content = inject(CourseContentService);

  /** Existing assignment to edit; null to create a new one. */
  @Input() assignment: AssignmentResponse | null = null;
  /** Courses to pick from when creating from the Assignments screen. */
  @Input() offerings: CourseOfferingResponse[] = [];
  /** A fixed course (course workspace) — the course picker is replaced by its label. */
  @Input() fixedOfferingId: string | null = null;
  @Input() fixedOfferingLabel = '';
  @Input() presetWeekId: string | null = null;
  @Output() saved = new EventEmitter<AssignmentResponse>();
  @Output() closed = new EventEmitter<void>();

  readonly fileIcon = fileIcon;
  readonly weeks = signal<WeekResponse[]>([]);
  readonly brief = signal<File | null>(null);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);

  readonly form = this.fb.group({
    offeringId: ['', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    dueDate: ['', Validators.required],
    maxMarks: [100, [Validators.required, Validators.min(1), Validators.max(1000)]],
    weekId: [''],
  });

  get isEdit(): boolean {
    return !!this.assignment;
  }

  get courseLocked(): boolean {
    return this.isEdit || !!this.fixedOfferingId;
  }

  ngOnInit(): void {
    const a = this.assignment;
    if (a) {
      this.form.reset({
        offeringId: a.offeringId,
        title: a.title,
        description: a.description ?? '',
        dueDate: toLocalInput(new Date(a.dueDate)),
        maxMarks: a.maxMarks,
        weekId: a.weekId ?? '',
      });
    } else {
      const due = new Date(Date.now() + 7 * 86400000);
      due.setHours(23, 59, 0, 0);
      this.form.reset({
        offeringId: this.fixedOfferingId ?? this.offerings[0]?.id ?? '',
        title: '',
        description: '',
        dueDate: toLocalInput(due),
        maxMarks: 100,
        weekId: this.presetWeekId ?? '',
      });
    }
    if (this.courseLocked) this.form.controls.offeringId.disable();
    this.loadWeeks(this.form.getRawValue().offeringId ?? '');
    this.form.controls.offeringId.valueChanges.subscribe((id) => {
      this.form.controls.weekId.setValue('');
      this.loadWeeks(id ?? '');
    });
  }

  courseLabel(): string {
    if (this.fixedOfferingLabel) return this.fixedOfferingLabel;
    const a = this.assignment;
    return a ? `${a.courseCode} — ${a.courseName}` : '';
  }

  pickBrief(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    input.value = '';
    if (f && f.size > MAX_BYTES) {
      this.error.set('That file is larger than 20MB.');
      return;
    }
    this.error.set(null);
    this.brief.set(f);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      offeringId: v.offeringId ?? undefined,
      title: (v.title ?? '').trim(),
      description: v.description || null,
      dueDate: new Date(v.dueDate ?? '').toISOString(),
      maxMarks: Number(v.maxMarks),
      weekId: v.weekId || null,
    };
    this.saving.set(true);
    this.error.set(null);
    let savedOk = false;
    const save$ = this.assignment ? this.grading.update(this.assignment.id, body) : this.grading.create(body);
    save$
      .pipe(switchMap((saved): Observable<AssignmentResponse> => {
        // From here on a retry must update this assignment, never create a second one.
        savedOk = true;
        this.assignment = saved;
        this.form.controls.offeringId.disable({ emitEvent: false });
        const file = this.brief();
        return file ? this.grading.uploadBrief(saved.id, file) : of(saved);
      }))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.saved.emit(saved);
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(savedOk
            ? errorMessage(err, 'The assignment was saved, but the file could not be uploaded.') + ' Save again to retry the upload.'
            : errorMessage(err, 'Could not save this assignment.'));
        },
      });
  }

  private loadWeeks(offeringId: string): void {
    this.weeks.set([]);
    if (!offeringId) return;
    this.content.listWeeks(offeringId).subscribe({ next: (w) => this.weeks.set([...w].sort((a, b) => a.weekNumber - b.weekNumber)) });
  }
}

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
