import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CourseCategoryResponse,
  DepartmentResponse,
  FacultyResponse,
  GradingScaleResponse,
  ReferenceCounts,
  ReferenceStatus,
} from '../../core/models/reference-data.model';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, statusOptions } from '../../shared/filters';

type Tab = 'faculties' | 'departments' | 'grading' | 'categories';
type TabItem = FacultyResponse | DepartmentResponse | GradingScaleResponse | CourseCategoryResponse;

const TAB_LABELS: Record<Tab, string> = {
  faculties: 'Faculties',
  departments: 'Departments',
  grading: 'Grading Scale',
  categories: 'Course Categories',
};

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/** Per-tab server-paginated list state — one page of rows at a time, not the whole table. */
interface ListState<T> {
  items: T[];
  page: number;
  totalElements: number;
  totalPages: number;
  loading: boolean;
  error: string | null;
}

function freshList<T>(): ListState<T> {
  return { items: [], page: 0, totalElements: 0, totalPages: 0, loading: true, error: null };
}

/** Full CRUD (not just add) for all 4 reference-data tabs, with per-tab active/inactive
 * counts at a glance — these are small, admin-maintained lookup tables shared across
 * every other service, so getting them right matters more than their size suggests.
 * Each tab is paginated and searched server-side (base-service's /faculties etc.) rather
 * than loading the whole table and filtering it in the browser. */
@Component({
  selector: 'app-reference-data',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    CommonModule,
    SearchableSelectComponent,
    SearchInputComponent,
    RowMenuComponent,
    FilterBarComponent,
    PagerComponent,
  ],
  templateUrl: './reference-data.component.html',
})
export class ReferenceDataComponent implements OnInit {
  private fb = inject(FormBuilder);
  private referenceDataService = inject(ReferenceDataService);

  readonly tabLabels = TAB_LABELS;
  readonly tabs: Tab[] = ['faculties', 'departments', 'grading', 'categories'];
  readonly activeTab = signal<Tab>('faculties');
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.patchActiveList({ page: 0 });
    this.load();
  }

  readonly facultyList = signal<ListState<FacultyResponse>>(freshList());
  readonly departmentList = signal<ListState<DepartmentResponse>>(freshList());
  readonly gradingList = signal<ListState<GradingScaleResponse>>(freshList());
  readonly categoryList = signal<ListState<CourseCategoryResponse>>(freshList());

  readonly facultyCounts = signal<ReferenceCounts | null>(null);
  readonly departmentCounts = signal<ReferenceCounts | null>(null);
  readonly gradingCounts = signal<ReferenceCounts | null>(null);
  readonly categoryCounts = signal<ReferenceCounts | null>(null);

  /** Every faculty, unpaginated — small enough to load once and reuse for both the
   * Departments tab's Faculty filter and the add/edit dialog's Faculty picker. */
  readonly allFaculties = signal<FacultyResponse[]>([]);
  readonly facultyOptions = computed(() => this.allFaculties().map((f) => ({ value: f.id, label: `${f.name} (${f.code})` })));
  readonly facultyFilterOptions = computed(() => this.allFaculties().map((f) => ({ value: f.id, label: f.name, sublabel: f.code })));

  readonly searchFilter = signal('');
  readonly statusFilter = signal<ReferenceStatus | ''>('');
  readonly facultyFilter = signal('');
  readonly statusFilterOptions = statusOptions(['ACTIVE', 'INACTIVE']);

  private searchDebounce: ReturnType<typeof setTimeout> | undefined;

  readonly activeFilterCount = computed(() =>
    countActive(this.searchFilter().trim(), this.statusFilter(), this.activeTab() === 'departments' ? this.facultyFilter() : ''),
  );

  readonly activeList = computed<ListState<TabItem>>(() => {
    switch (this.activeTab()) {
      case 'faculties': return this.facultyList();
      case 'departments': return this.departmentList();
      case 'grading': return this.gradingList();
      case 'categories': return this.categoryList();
    }
  });

  readonly activeTabCounts = computed<ReferenceCounts>(() => {
    const c = {
      faculties: this.facultyCounts(),
      departments: this.departmentCounts(),
      grading: this.gradingCounts(),
      categories: this.categoryCounts(),
    }[this.activeTab()];
    return c ?? { total: 0, active: 0, inactive: 0 };
  });

  readonly filterSummary = computed(() => {
    const list = this.activeList();
    if (list.loading || list.error) return '';
    const label = this.tabLabels[this.activeTab()].toLowerCase();
    return `${list.totalElements} ${label}${this.activeFilterCount() ? ' match' : ' in total'}`;
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
    this.referenceDataService.listFaculties({ size: 100 }).subscribe({ next: (p) => this.allFaculties.set(p.content) });
    this.load();
    this.loadCounts();
  }

  selectTab(tab: Tab): void {
    this.activeTab.set(tab);
    clearTimeout(this.searchDebounce);
    this.searchFilter.set('');
    this.statusFilter.set('');
    this.facultyFilter.set('');
    this.load();
    this.loadCounts();
  }

  onSearchInput(value: string): void {
    this.searchFilter.set(value);
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(), SEARCH_DEBOUNCE_MS);
  }

  onStatusFilterChange(value: ReferenceStatus | ''): void {
    this.statusFilter.set(value);
    this.load();
  }

  onFacultyFilterChange(value: string): void {
    this.facultyFilter.set(value);
    this.load();
  }

  clearFilters(): void {
    clearTimeout(this.searchDebounce);
    this.searchFilter.set('');
    this.statusFilter.set('');
    this.facultyFilter.set('');
    this.load();
  }

  goToPage(page: number): void {
    this.patchActiveList({ page });
    this.load();
  }

  /** Reloads the active tab's current page with the current filters. */
  load(): void {
    const status = this.statusFilter() || undefined;
    const q = this.searchFilter().trim() || undefined;
    const page = this.activeList().page;
    this.patchActiveList({ loading: true, error: null });

    switch (this.activeTab()) {
      case 'faculties':
        this.referenceDataService.listFaculties({ status, q, page, size: this.pageSize() }).subscribe({
          next: (p) => this.facultyList.set({ items: p.content, page: p.number, totalElements: p.totalElements, totalPages: p.totalPages, loading: false, error: null }),
          error: () => this.facultyList.update((s) => ({ ...s, loading: false, error: 'Could not load faculties.' })),
        });
        return;
      case 'departments':
        this.referenceDataService.listDepartments(this.facultyFilter() || undefined, { status, q, page, size: this.pageSize() }).subscribe({
          next: (p) => this.departmentList.set({ items: p.content, page: p.number, totalElements: p.totalElements, totalPages: p.totalPages, loading: false, error: null }),
          error: () => this.departmentList.update((s) => ({ ...s, loading: false, error: 'Could not load departments.' })),
        });
        return;
      case 'grading':
        this.referenceDataService.listGradingScales({ status, q, page, size: this.pageSize() }).subscribe({
          next: (p) => this.gradingList.set({ items: p.content, page: p.number, totalElements: p.totalElements, totalPages: p.totalPages, loading: false, error: null }),
          error: () => this.gradingList.update((s) => ({ ...s, loading: false, error: 'Could not load grading scales.' })),
        });
        return;
      case 'categories':
        this.referenceDataService.listCourseCategories({ status, q, page, size: this.pageSize() }).subscribe({
          next: (p) => this.categoryList.set({ items: p.content, page: p.number, totalElements: p.totalElements, totalPages: p.totalPages, loading: false, error: null }),
          error: () => this.categoryList.update((s) => ({ ...s, loading: false, error: 'Could not load course categories.' })),
        });
        return;
    }
  }

  /** Grand totals for the active tab, independent of its current page/filter. */
  loadCounts(): void {
    switch (this.activeTab()) {
      case 'faculties':
        this.referenceDataService.facultyCounts().subscribe({ next: (c) => this.facultyCounts.set(c) });
        return;
      case 'departments':
        this.referenceDataService.departmentCounts().subscribe({ next: (c) => this.departmentCounts.set(c) });
        return;
      case 'grading':
        this.referenceDataService.gradingScaleCounts().subscribe({ next: (c) => this.gradingCounts.set(c) });
        return;
      case 'categories':
        this.referenceDataService.courseCategoryCounts().subscribe({ next: (c) => this.categoryCounts.set(c) });
        return;
    }
  }

  private patchActiveList(patch: Partial<ListState<TabItem>>): void {
    switch (this.activeTab()) {
      case 'faculties': this.facultyList.update((s) => ({ ...s, ...patch }) as ListState<FacultyResponse>); return;
      case 'departments': this.departmentList.update((s) => ({ ...s, ...patch }) as ListState<DepartmentResponse>); return;
      case 'grading': this.gradingList.update((s) => ({ ...s, ...patch }) as ListState<GradingScaleResponse>); return;
      case 'categories': this.categoryList.update((s) => ({ ...s, ...patch }) as ListState<CourseCategoryResponse>); return;
    }
  }

  /** After any create/update/delete: the mutated row may no longer belong on this page
   * (new sort position, filtered out, etc.), so reload the page wholesale instead of
   * patching the in-memory array — plus the grand totals, which that mutation changed. */
  private refreshAfterMutation(): void {
    this.load();
    this.loadCounts();
    if (this.activeTab() === 'faculties') {
      this.referenceDataService.listFaculties({ size: 100 }).subscribe({ next: (p) => this.allFaculties.set(p.content) });
    }
  }

  openAddDialog(): void {
    this.editingId.set(null);
    this.addError.set(null);
    this.facultyForm.reset({ name: '', code: '', status: 'ACTIVE' });
    this.departmentForm.reset({ name: '', code: '', facultyId: this.allFaculties()[0]?.id ?? '', status: 'ACTIVE' });
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
      this.facultyForm.disable();
      const { name, code, status } = this.facultyForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateFaculty(editingId, { name: name!, code: code!, status: status! })
        : this.referenceDataService.createFaculty({ name: name!, code: code! });
      req$.subscribe({
        next: (saved) => this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`),
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else if (tab === 'departments') {
      if (this.departmentForm.invalid) { this.addError.set('Enter a name, code and faculty.'); return; }
      this.addSubmitting.set(true);
      this.departmentForm.disable();
      const { name, code, facultyId, status } = this.departmentForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateDepartment(editingId, { name: name!, code: code!, facultyId: facultyId!, status: status! })
        : this.referenceDataService.createDepartment({ name: name!, code: code!, facultyId: facultyId! });
      req$.subscribe({
        next: (saved) => this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`),
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else if (tab === 'grading') {
      if (this.gradingForm.invalid) { this.addError.set('Enter a scale name and its bands.'); return; }
      this.addSubmitting.set(true);
      this.gradingForm.disable();
      const { name, bands, status } = this.gradingForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateGradingScale(editingId, { name: name!, bands: bands!, status: status! })
        : this.referenceDataService.createGradingScale({ name: name!, bands: bands! });
      req$.subscribe({
        next: (saved) => this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`),
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    } else {
      if (this.categoryForm.invalid) { this.addError.set('Enter a name and a description.'); return; }
      this.addSubmitting.set(true);
      this.categoryForm.disable();
      const { name, description, status } = this.categoryForm.getRawValue();
      const req$ = editingId
        ? this.referenceDataService.updateCourseCategory(editingId, { name: name!, description: description!, status: status! })
        : this.referenceDataService.createCourseCategory({ name: name!, description: description! });
      req$.subscribe({
        next: (saved) => this.finishAdd(editingId ? `${saved.name} updated.` : `${saved.name} added.`),
        error: (err: HttpErrorResponse) => this.failAdd(err),
      });
    }
  }

  deleteEntry(tab: Tab, item: { id: string; name: string }): void {
    if (!confirm(`Delete "${item.name}"? This cannot be undone.`)) return;
    const onSuccess = () => {
      this.refreshAfterMutation();
      this.showToast(`"${item.name}" deleted.`, false);
    };
    const onError = (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this entry.', true);

    if (tab === 'faculties') {
      this.referenceDataService.deleteFaculty(item.id).subscribe({ next: onSuccess, error: onError });
    } else if (tab === 'departments') {
      this.referenceDataService.deleteDepartment(item.id).subscribe({ next: onSuccess, error: onError });
    } else if (tab === 'grading') {
      this.referenceDataService.deleteGradingScale(item.id).subscribe({ next: onSuccess, error: onError });
    } else {
      this.referenceDataService.deleteCourseCategory(item.id).subscribe({ next: onSuccess, error: onError });
    }
  }

  private finishAdd(message: string): void {
    this.addSubmitting.set(false);
    this.enableEntryForms();
    this.showEntryDialog.set(false);
    this.refreshAfterMutation();
    this.showToast(message, false);
  }

  private failAdd(err: HttpErrorResponse): void {
    this.addSubmitting.set(false);
    this.enableEntryForms();
    this.addError.set(err.error?.message ?? 'Could not save this entry. Please try again.');
  }

  /** Only one of these four is ever visible at a time, so re-enabling all of them after a
   * submit completes (success or error) is simpler than tracking which tab was active. */
  private enableEntryForms(): void {
    this.facultyForm.enable();
    this.departmentForm.enable();
    this.gradingForm.enable();
    this.categoryForm.enable();
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
