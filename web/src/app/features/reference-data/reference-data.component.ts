import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CourseCategoryResponse,
  DepartmentResponse,
  FacultyResponse,
  GradingScaleResponse,
  ReferenceStatus,
} from '../../core/models/reference-data.model';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';

type Tab = 'faculties' | 'departments' | 'grading' | 'categories';

const TAB_LABELS: Record<Tab, string> = {
  faculties: 'Faculties',
  departments: 'Departments',
  grading: 'Grading Scale',
  categories: 'Course Categories',
};

interface TabCounts {
  total: number;
  active: number;
  inactive: number;
}

/** Full CRUD (not just add) for all 4 reference-data tabs, with per-tab active/inactive
 * counts at a glance — these are small, admin-maintained lookup tables shared across
 * every other service, so getting them right matters more than their size suggests. */
@Component({
  selector: 'app-reference-data',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, SearchableSelectComponent],
  templateUrl: './reference-data.component.html',
})
export class ReferenceDataComponent implements OnInit {
  private fb = inject(FormBuilder);
  private referenceDataService = inject(ReferenceDataService);

  readonly tabLabels = TAB_LABELS;
  readonly tabs: Tab[] = ['faculties', 'departments', 'grading', 'categories'];
  readonly activeTab = signal<Tab>('faculties');

  readonly faculties = signal<FacultyResponse[]>([]);
  readonly departments = signal<DepartmentResponse[]>([]);
  readonly gradingScales = signal<GradingScaleResponse[]>([]);
  readonly courseCategories = signal<CourseCategoryResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly facultyOptions = computed(() => this.faculties().map((f) => ({ value: f.id, label: `${f.name} (${f.code})` })));

  private countsOf(items: { status: ReferenceStatus }[]): TabCounts {
    const active = items.filter((i) => i.status === 'ACTIVE').length;
    return { total: items.length, active, inactive: items.length - active };
  }
  readonly facultyCounts = computed(() => this.countsOf(this.faculties()));
  readonly departmentCounts = computed(() => this.countsOf(this.departments()));
  readonly gradingCounts = computed(() => this.countsOf(this.gradingScales()));
  readonly categoryCounts = computed(() => this.countsOf(this.courseCategories()));
  readonly activeTabCounts = computed<TabCounts>(() => {
    switch (this.activeTab()) {
      case 'faculties': return this.facultyCounts();
      case 'departments': return this.departmentCounts();
      case 'grading': return this.gradingCounts();
      case 'categories': return this.categoryCounts();
    }
  });

  readonly showEntryDialog = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly addError = signal<string | null>(null);
  readonly addSubmitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  readonly facultyForm = this.fb.group({
    name: ['', Validators.required],
    code: ['', Validators.required],
    status: ['ACTIVE' as ReferenceStatus, Validators.required],
  });
  readonly departmentForm = this.fb.group({
    name: ['', Validators.required],
    code: ['', Validators.required],
    facultyId: ['', Validators.required],
    status: ['ACTIVE' as ReferenceStatus, Validators.required],
  });
  readonly gradingForm = this.fb.group({
    name: ['', Validators.required],
    bands: ['', Validators.required],
    status: ['ACTIVE' as ReferenceStatus, Validators.required],
  });
  readonly categoryForm = this.fb.group({
    name: ['', Validators.required],
    description: ['', Validators.required],
    status: ['ACTIVE' as ReferenceStatus, Validators.required],
  });

  ngOnInit(): void {
    this.loadAll();
  }

  selectTab(tab: Tab): void {
    this.activeTab.set(tab);
  }

  loadAll(): void {
    this.loading.set(true);
    this.loadError.set(null);
    let remaining = 4;
    const done = () => {
      remaining -= 1;
      if (remaining === 0) this.loading.set(false);
    };
    const fail = () => {
      this.loadError.set('Could not load reference data.');
      this.loading.set(false);
    };
    this.referenceDataService.listFaculties().subscribe({
      next: (v) => { this.faculties.set(v); done(); },
      error: fail,
    });
    this.referenceDataService.listDepartments().subscribe({
      next: (v) => { this.departments.set(v); done(); },
      error: fail,
    });
    this.referenceDataService.listGradingScales().subscribe({
      next: (v) => { this.gradingScales.set(v); done(); },
      error: fail,
    });
    this.referenceDataService.listCourseCategories().subscribe({
      next: (v) => { this.courseCategories.set(v); done(); },
      error: fail,
    });
  }

  openAddDialog(): void {
    this.editingId.set(null);
    this.addError.set(null);
    this.facultyForm.reset({ name: '', code: '', status: 'ACTIVE' });
    this.departmentForm.reset({ name: '', code: '', facultyId: this.faculties()[0]?.id ?? '', status: 'ACTIVE' });
    this.gradingForm.reset({ name: '', bands: '', status: 'ACTIVE' });
    this.categoryForm.reset({ name: '', description: '', status: 'ACTIVE' });
    this.showEntryDialog.set(true);
  }

  openEditDialog(tab: Tab, item: FacultyResponse | DepartmentResponse | GradingScaleResponse | CourseCategoryResponse): void {
    this.editingId.set(item.id);
    this.addError.set(null);
    if (tab === 'faculties') {
      const f = item as FacultyResponse;
      this.facultyForm.reset({ name: f.name, code: f.code, status: f.status });
    } else if (tab === 'departments') {
      const d = item as DepartmentResponse;
      this.departmentForm.reset({ name: d.name, code: d.code, facultyId: d.facultyId, status: d.status });
    } else if (tab === 'grading') {
      const g = item as GradingScaleResponse;
      this.gradingForm.reset({ name: g.name, bands: g.bands, status: g.status });
    } else {
      const c = item as CourseCategoryResponse;
      this.categoryForm.reset({ name: c.name, description: c.description, status: c.status });
    }
    this.showEntryDialog.set(true);
  }

  submitAdd(): void {
    const tab = this.activeTab();
    const editingId = this.editingId();
    this.addError.set(null);

    if (tab === 'faculties') {
      if (this.facultyForm.invalid) { this.addError.set('Enter a name and a short code.'); return; }
      this.addSubmitting.set(true);
      const { name, code, status } = this.facultyForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateFaculty(editingId, { name: name!, code: code!, status: status! })
        : this.referenceDataService.createFaculty({ name: name!, code: code! });
      req$.subscribe({
        next: (saved) => {
          this.faculties.update((list) =>
            [...list.filter((x) => x.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name)),
          );
          this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`);
        },
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else if (tab === 'departments') {
      if (this.departmentForm.invalid) { this.addError.set('Enter a name, code and faculty.'); return; }
      this.addSubmitting.set(true);
      const { name, code, facultyId, status } = this.departmentForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateDepartment(editingId, { name: name!, code: code!, facultyId: facultyId!, status: status! })
        : this.referenceDataService.createDepartment({ name: name!, code: code!, facultyId: facultyId! });
      req$.subscribe({
        next: (saved) => {
          this.departments.update((list) =>
            [...list.filter((x) => x.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name)),
          );
          this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`);
        },
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else if (tab === 'grading') {
      if (this.gradingForm.invalid) { this.addError.set('Enter a scale name and its bands.'); return; }
      this.addSubmitting.set(true);
      const { name, bands, status } = this.gradingForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateGradingScale(editingId, { name: name!, bands: bands!, status: status! })
        : this.referenceDataService.createGradingScale({ name: name!, bands: bands! });
      req$.subscribe({
        next: (saved) => {
          this.gradingScales.update((list) =>
            [...list.filter((x) => x.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name)),
          );
          this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`);
        },
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else {
      if (this.categoryForm.invalid) { this.addError.set('Enter a name and a description.'); return; }
      this.addSubmitting.set(true);
      const { name, description, status } = this.categoryForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateCourseCategory(editingId, { name: name!, description: description!, status: status! })
        : this.referenceDataService.createCourseCategory({ name: name!, description: description! });
      req$.subscribe({
        next: (saved) => {
          this.courseCategories.update((list) =>
            [...list.filter((x) => x.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name)),
          );
          this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`);
        },
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    }
  }

  deleteEntry(tab: Tab, item: { id: string; name: string }): void {
    if (!confirm(`Delete "${item.name}"? This cannot be undone.`)) return;
    const onSuccess = () => this.showToast(`"${item.name}" deleted.`, false);
    const onError = (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this entry.', true);

    if (tab === 'faculties') {
      this.referenceDataService.deleteFaculty(item.id).subscribe({
        next: () => { this.faculties.update((list) => list.filter((x) => x.id !== item.id)); onSuccess(); },
        error: onError,
      });
    } else if (tab === 'departments') {
      this.referenceDataService.deleteDepartment(item.id).subscribe({
        next: () => { this.departments.update((list) => list.filter((x) => x.id !== item.id)); onSuccess(); },
        error: onError,
      });
    } else if (tab === 'grading') {
      this.referenceDataService.deleteGradingScale(item.id).subscribe({
        next: () => { this.gradingScales.update((list) => list.filter((x) => x.id !== item.id)); onSuccess(); },
        error: onError,
      });
    } else {
      this.referenceDataService.deleteCourseCategory(item.id).subscribe({
        next: () => { this.courseCategories.update((list) => list.filter((x) => x.id !== item.id)); onSuccess(); },
        error: onError,
      });
    }
  }

  private finishAdd(message: string): void {
    this.addSubmitting.set(false);
    this.showEntryDialog.set(false);
    this.showToast(message, false);
  }

  private failAdd(err: HttpErrorResponse): void {
    this.addSubmitting.set(false);
    this.addError.set(err.error?.message ?? 'Could not save this entry. Please try again.');
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
