import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { AssignmentResponse, AssignmentStatus } from '../../core/models/grading.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { GradingService } from '../../core/services/grading.service';
import { errorMessage, saveBlob, Toaster } from '../../shared/ui';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, matchesText, statusOptions } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Wireframe lecturerAssignments: every assignment across the lecturer's course
 * offerings. New assignments start as Draft; Publish makes them visible to enrolled
 * students (and notifies them), Close stops new submissions.
 */
@Component({
  selector: 'app-lecturer-assignments',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink, SearchableSelectComponent, SearchInputComponent, RowMenuComponent, FilterBarComponent, PagerComponent],
  templateUrl: './lecturer-assignments.component.html',
})
export class LecturerAssignmentsComponent {
  private fb = inject(FormBuilder);
  private grading = inject(GradingService);
  private academic = inject(AcademicStructureService);
  private auth = inject(AuthService);
  private loadRequestId = 0;

  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly offerings = signal<CourseOfferingResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly courseFilter = signal('');
  readonly statusFilter = signal<AssignmentStatus | ''>('');
  readonly titleFilter = signal('');
  readonly courseFilterOptions = computed<SearchableSelectOption[]>(() =>
    this.offerings().map((o) => ({ value: o.id, label: `${o.code} — ${o.name}`, sublabel: o.academicYearName })),
  );
  readonly statusFilterOptions = statusOptions(['DRAFT', 'OPEN', 'CLOSED']);
  readonly editing = signal<AssignmentResponse | 'new' | null>(null);
  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  /** The current page, further narrowed by title search — title isn't a server-filterable
   * field on this endpoint, so it applies only within the loaded page (course/status are
   * native fields and are sent to the server instead). */
  readonly filtered = computed(() => this.assignments().filter((a) => matchesText(this.titleFilter(), a.title, a.courseCode)));

  readonly activeFilterCount = computed(() => countActive(this.courseFilter(), this.statusFilter(), this.titleFilter().trim()));
  readonly filterSummary = computed(() => `${this.totalElements()} assignment${this.totalElements() === 1 ? '' : 's'}${this.activeFilterCount() ? ' match' : ' in total'}`);

  clearFilters(): void {
    this.courseFilter.set('');
    this.statusFilter.set('');
    this.titleFilter.set('');
    this.page.set(0);
    this.load();
  }

  onCourseFilterChange(value: string): void {
    this.courseFilter.set(value);
    this.page.set(0);
    this.load();
  }

  onStatusFilterChange(value: AssignmentStatus | ''): void {
    this.statusFilter.set(value);
    this.page.set(0);
    this.load();
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.load();
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  readonly form = this.fb.group({
    offeringId: ['', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    dueDate: ['', Validators.required],
    maxMarks: [100, [Validators.required, Validators.min(1), Validators.max(1000)]],
  });

  constructor() {
    const isAdmin = this.auth.currentUser()?.role === 'ADMIN';
    this.academic.listCourseOfferings({ mine: !isAdmin }).subscribe({
      next: (v) => this.offerings.set(v.filter((o) => o.academicYearStatus !== 'ARCHIVED')),
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const requestId = ++this.loadRequestId;
    this.grading
      .list({ offeringId: this.courseFilter() || undefined, status: this.statusFilter(), page: this.page(), size: this.pageSize() })
      .subscribe({
        next: (p) => {
          if (requestId !== this.loadRequestId) return; // a newer filter/page change already superseded this request
          this.assignments.set(p.content);
          this.totalElements.set(p.totalElements);
          this.totalPages.set(p.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          if (requestId !== this.loadRequestId) return;
          this.loadError.set(errorMessage(err, 'Could not load assignments.'));
          this.loading.set(false);
        },
      });
  }

  openNew(): void {
    const due = new Date(Date.now() + 7 * 86400000);
    due.setHours(23, 59, 0, 0);
    this.form.reset({ offeringId: this.offerings()[0]?.id ?? '', title: '', description: '', dueDate: toLocalInput(due), maxMarks: 100 });
    this.form.controls.offeringId.enable();
    this.dialogError.set(null);
    this.editing.set('new');
  }

  openEdit(a: AssignmentResponse): void {
    this.form.reset({
      offeringId: a.offeringId,
      title: a.title,
      description: a.description ?? '',
      dueDate: toLocalInput(new Date(a.dueDate)),
      maxMarks: a.maxMarks,
    });
    this.form.controls.offeringId.disable();
    this.dialogError.set(null);
    this.editing.set(a);
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const body = {
      offeringId: v.offeringId ?? undefined,
      title: v.title ?? '',
      description: v.description || null,
      dueDate: new Date(v.dueDate ?? '').toISOString(),
      maxMarks: Number(v.maxMarks),
    };
    const editing = this.editing();
    this.submitting.set(true);
    this.form.disable();
    const call = editing === 'new' ? this.grading.create(body) : this.grading.update((editing as AssignmentResponse).id, body);
    call.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.editing.set(null);
        this.toaster.show(editing === 'new' ? `"${saved.title}" created as a draft.` : `"${saved.title}" updated.`);
        this.load();
      },
      error: (err) => {
        this.submitting.set(false);
        this.form.enable();
        // offeringId is permanently locked once editing an existing assignment (see openEdit).
        if (editing !== 'new') this.form.controls.offeringId.disable();
        this.dialogError.set(errorMessage(err, 'Could not save this assignment.'));
      },
    });
  }

  setStatus(a: AssignmentResponse, status: AssignmentStatus): void {
    this.grading.setStatus(a.id, status).subscribe({
      next: () => {
        const verb = status === 'OPEN' ? (a.status === 'DRAFT' ? 'published — students notified' : 'reopened') : status === 'CLOSED' ? 'closed' : 'moved back to draft';
        this.toaster.show(`"${a.title}" ${verb}.`);
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not change the status.'), true),
    });
  }

  remove(a: AssignmentResponse): void {
    if (!confirm(`Delete "${a.title}"?`)) return;
    this.grading.delete(a.id).subscribe({
      next: () => {
        this.toaster.show(`"${a.title}" deleted.`);
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this assignment.'), true),
    });
  }

  uploadBrief(a: AssignmentResponse, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.grading.uploadBrief(a.id, file).subscribe({
      next: () => {
        this.toaster.show(`Brief attached to "${a.title}".`);
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not upload the brief.'), true),
    });
  }

  downloadBrief(a: AssignmentResponse): void {
    this.grading.downloadBrief(a.id).subscribe({
      next: (blob) => saveBlob(blob, a.briefFileName ?? 'brief'),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not download the brief.'), true),
    });
  }
}

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
