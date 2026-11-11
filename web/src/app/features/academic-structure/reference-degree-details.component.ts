import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CourseCatalogResponse,
  DegreeProgramResponse,
  ProgramCourseResponse,
  ProgramStatus,
  RequirementType,
} from '../../core/models/academic-structure.model';
import { DepartmentResponse } from '../../core/models/reference-data.model';
import { AuthService } from '../../core/services/auth.service';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, plural, statusOptions } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/**
 * "Reference Degree Details" — the catalog of degree templates (name, department,
 * default duration/credits), separate from the year-aware "Degree Programs" screen.
 * Admins see every reference degree with a department filter; lecturers (e.g.
 * Department Heads) see only degrees in a department they're scoped to — the backend
 * (DegreeProgramService) already enforces this, this page just exposes the filter.
 * The list is paginated and searched server-side rather than loading every reference
 * degree and filtering it in the browser.
 */
@Component({
  selector: 'app-reference-degree-details',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, SearchableSelectComponent, SearchInputComponent, RowMenuComponent, FilterBarComponent, PagerComponent],
  templateUrl: './reference-degree-details.component.html',
})
export class ReferenceDegreeDetailsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);
  private referenceDataService = inject(ReferenceDataService);
  readonly auth = inject(AuthService);
  private loadRequestId = 0;

  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly programs = signal<DegreeProgramResponse[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly departments = signal<DepartmentResponse[]>([]);
  readonly catalog = signal<CourseCatalogResponse[]>([]);
  readonly curriculum = signal<ProgramCourseResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  /** The program shown in the curriculum panel — held directly rather than looked up
   * from the current page, so it stays correct even when a just-created or just-edited
   * program doesn't land on the page currently in view. */
  readonly selectedProgram = signal<DegreeProgramResponse | null>(null);
  readonly curriculumLoading = signal(false);
  readonly departmentFilter = signal<string>('');

  readonly searchFilter = signal('');
  readonly statusFilter = signal<ProgramStatus | ''>('');
  readonly statusFilterOptions = statusOptions(['ACTIVE', 'INACTIVE']);
  private searchDebounce: ReturnType<typeof setTimeout> | undefined;

  readonly activeFilterCount = computed(() => countActive(this.searchFilter().trim(), this.departmentFilter(), this.statusFilter()));
  readonly filterSummary = computed(() => {
    if (this.loading() || this.loadError()) return '';
    return `${plural(this.totalElements(), 'degree')}${this.activeFilterCount() ? ' match' : ' in total'}`;
  });

  readonly totalCore = computed(() => this.curriculum().filter((c) => c.requirementType === 'CORE').length);
  readonly totalElective = computed(() => this.curriculum().filter((c) => c.requirementType === 'ELECTIVE').length);

  readonly departmentFilterOptions = computed(() => this.departments().map((d) => ({ value: d.id, label: d.name, sublabel: d.facultyName })));
  readonly departmentFieldOptions = computed(() => this.departments().map((d) => ({ value: d.id, label: `${d.name} (${d.facultyName})` })));
  readonly catalogCourseOptions = computed(() => this.catalog().map((c) => ({ value: c.id, label: `${c.code} - ${c.name}` })));

  readonly showProgramDialog = signal(false);
  readonly editingProgramId = signal<string | null>(null);
  readonly showCurriculumDialog = signal(false);
  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  readonly programForm = this.fb.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    departmentId: ['', Validators.required],
    durationYears: [4, [Validators.required, Validators.min(1)]],
    totalCredits: [120, [Validators.required, Validators.min(1)]],
    status: ['ACTIVE' as ProgramStatus, Validators.required],
  });

  readonly curriculumForm = this.fb.group({
    catalogCourseId: ['', Validators.required],
    requirementType: ['CORE' as RequirementType, Validators.required],
    recommendedYear: [1, [Validators.required, Validators.min(1)]],
    recommendedSemester: [1, [Validators.required, Validators.min(1)]],
    creditValue: [3, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.referenceDataService.listDepartments(undefined, { size: 100 }).subscribe({ next: (p) => this.departments.set(p.content) });
    this.academicStructureService.listCourseCatalog({ size: 200 }).subscribe({ next: (p) => this.catalog.set(p.content) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const requestId = ++this.loadRequestId;
    this.academicStructureService
      .listDegreePrograms({
        departmentId: this.departmentFilter() || null,
        status: this.statusFilter() || undefined,
        q: this.searchFilter().trim() || undefined,
        page: this.page(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (result) => {
          if (requestId !== this.loadRequestId) return; // a newer filter change already superseded this request
          this.programs.set(result.content);
          this.page.set(result.number);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);

          // Keep showing the same program's curriculum if it's still on this page
          // (a benign reload, e.g. after editing it); otherwise fall back to the
          // page's first entry, same as a fresh load.
          const stillVisible = result.content.find((p) => p.id === this.selectedProgram()?.id);
          if (stillVisible) this.selectProgram(stillVisible);
          else if (result.content.length) this.selectProgram(result.content[0]);
          else this.selectedProgram.set(null);
        },
        error: () => {
          if (requestId !== this.loadRequestId) return;
          this.loadError.set('Could not load reference degrees.');
          this.loading.set(false);
        },
      });
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.load();
  }

  onSearchInput(value: string): void {
    this.searchFilter.set(value);
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => { this.page.set(0); this.load(); }, SEARCH_DEBOUNCE_MS);
  }

  onStatusFilterChange(value: ProgramStatus | ''): void {
    this.statusFilter.set(value);
    this.page.set(0);
    this.load();
  }

  clearFilters(): void {
    clearTimeout(this.searchDebounce);
    this.searchFilter.set('');
    this.statusFilter.set('');
    this.departmentFilter.set('');
    this.page.set(0);
    this.load();
  }

  onDepartmentFilterChange(value: string): void {
    this.departmentFilter.set(value);
    this.page.set(0);
    this.load();
  }

  selectProgram(program: DegreeProgramResponse): void {
    this.selectedProgram.set(program);
    this.curriculumLoading.set(true);
    const id = program.id;
    this.academicStructureService.listCurriculum(id).subscribe({
      next: (v) => {
        if (this.selectedProgram()?.id !== id) return; // the user already switched to a different program
        this.curriculum.set(v);
        this.curriculumLoading.set(false);
      },
      error: () => {
        if (this.selectedProgram()?.id !== id) return;
        this.curriculumLoading.set(false);
      },
    });
  }

  departmentName(id: string): string {
    return this.departments().find((d) => d.id === id)?.name ?? '-';
  }

  openProgramDialog(): void {
    this.editingProgramId.set(null);
    this.dialogError.set(null);
    this.programForm.reset({
      code: '',
      name: '',
      departmentId: this.departmentFilter() || this.departments()[0]?.id || '',
      durationYears: 4,
      totalCredits: 120,
      status: 'ACTIVE',
    });
    this.programForm.get('code')?.enable();
    this.programForm.get('departmentId')?.enable();
    this.showProgramDialog.set(true);
  }

  openEditProgramDialog(program: DegreeProgramResponse): void {
    this.editingProgramId.set(program.id);
    this.dialogError.set(null);
    this.programForm.reset({
      code: program.code,
      name: program.name,
      departmentId: program.departmentId,
      durationYears: program.durationYears,
      totalCredits: program.totalCredits,
      status: program.status as ProgramStatus,
    });
    // code and department are permanent once created — see UpdateDegreeProgramRequest.
    this.programForm.get('code')?.disable();
    this.programForm.get('departmentId')?.disable();
    this.showProgramDialog.set(true);
  }

  submitProgram(): void {
    if (this.programForm.invalid) {
      this.dialogError.set('Enter a program code, name and department.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    this.programForm.disable();
    const { code, name, departmentId, durationYears, totalCredits, status } = this.programForm.getRawValue();
    const editingId = this.editingProgramId();

    if (editingId) {
      this.academicStructureService
        .updateDegreeProgram(editingId, { name: name!, durationYears: durationYears!, totalCredits: totalCredits!, status: status! })
        .subscribe({
          next: (updated) => {
            this.submitting.set(false);
            this.unlockProgramForm();
            this.showProgramDialog.set(false);
            this.showToast(`"${updated.name}" updated.`, false);
            this.load();
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.unlockProgramForm();
            this.dialogError.set(err.error?.message ?? 'Could not update this reference degree.');
          },
        });
      return;
    }

    this.academicStructureService
      .createDegreeProgram({
        code: code!.toUpperCase(),
        name: name!,
        departmentId: departmentId!,
        durationYears: durationYears!,
        totalCredits: totalCredits!,
        status: status!,
      })
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.unlockProgramForm();
          this.showProgramDialog.set(false);
          this.showToast(`Reference degree "${created.name}" created.`, false);
          this.selectProgram(created);
          this.page.set(0);
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.unlockProgramForm();
          this.dialogError.set(err.error?.message ?? 'Could not create the reference degree.');
        },
      });
  }

  /** code/departmentId are permanently locked once editing an existing degree (see
   * openEditProgramDialog) — re-enabling the whole form after a submit must not undo
   * that lock. */
  private unlockProgramForm(): void {
    this.programForm.enable();
    if (this.editingProgramId()) {
      this.programForm.get('code')?.disable();
      this.programForm.get('departmentId')?.disable();
    }
  }

  deleteProgram(program: DegreeProgramResponse): void {
    if (!confirm(`Delete reference degree "${program.name}"? This cannot be undone.`)) return;
    this.academicStructureService.deleteDegreeProgram(program.id).subscribe({
      next: () => {
        this.showToast(`"${program.name}" deleted.`, false);
        this.load();
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this reference degree.', true),
    });
  }

  openCurriculumDialog(): void {
    this.dialogError.set(null);
    this.curriculumForm.reset({
      catalogCourseId: this.catalog()[0]?.id ?? '',
      requirementType: 'CORE',
      recommendedYear: 1,
      recommendedSemester: 1,
      creditValue: this.catalog()[0]?.defaultCredits ?? 3,
    });
    this.showCurriculumDialog.set(true);
  }

  onCatalogCourseChange(id: string): void {
    const course = this.catalog().find((c) => c.id === id);
    if (course) this.curriculumForm.patchValue({ creditValue: course.defaultCredits });
  }

  submitCurriculum(): void {
    const program = this.selectedProgram();
    if (!program) return;
    if (this.curriculumForm.invalid) {
      this.dialogError.set('Choose a catalog course.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    this.curriculumForm.disable();
    const { catalogCourseId, requirementType, recommendedYear, recommendedSemester, creditValue } =
      this.curriculumForm.getRawValue();
    this.academicStructureService
      .addCurriculumEntry(program.id, {
        catalogCourseId: catalogCourseId!,
        requirementType: requirementType!,
        recommendedYear: recommendedYear!,
        recommendedSemester: recommendedSemester!,
        creditValue: creditValue!,
      })
      .subscribe({
        next: (created) => {
          this.curriculum.update((list) => [...list, created]);
          this.submitting.set(false);
          this.curriculumForm.enable();
          this.showCurriculumDialog.set(false);
          this.showToast(`${created.catalogCourseCode} added to curriculum.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.curriculumForm.enable();
          this.dialogError.set(err.error?.message ?? 'Could not add this course to the curriculum.');
        },
      });
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
