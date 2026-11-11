import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseCatalogResponse,
  CourseResponse,
  CourseStatus,
  DegreeProgramAcademicYearResponse,
  DegreeProgramResponse,
  DegreeProgramYearStatus,
  ProgramCourseResponse,
  RequirementType,
} from '../../core/models/academic-structure.model';
import { ScopeHolderResponse } from '../../core/models/roles.model';
import { UserResponse } from '../../core/models/user.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { GradingService } from '../../core/services/grading.service';
import { RolesService } from '../../core/services/roles.service';
import { UserService } from '../../core/services/user.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { countActive, matchesText, numberOptions, showingSummary, statusOptions, uniqueOptions } from '../../shared/filters';

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * "Program Management → Degree Programs" — which reference degrees (managed on the
 * "Reference Degree Details" page) continue for a given Academic Calendar year, with
 * any per-year overrides to the reference defaults. A degree not assigned here for a
 * year simply doesn't run that year — it still exists in the reference catalog.
 * Independent of the shared topbar year selector: this screen has its own filter,
 * including an "All Years" view (hence the Academic Year column).
 */
@Component({
  selector: 'app-degree-programs',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, SearchableSelectComponent, SearchInputComponent, RowMenuComponent, FilterBarComponent],
  templateUrl: './degree-programs.component.html',
})
export class DegreeProgramsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);
  private userService = inject(UserService);
  private rolesService = inject(RolesService);
  private gradingService = inject(GradingService);
  readonly auth = inject(AuthService);
  private loadRequestId = 0;

  /** Program Head: a system role held at DEGREE_PROGRAM scope — see
   * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 5. Assigning/removing one
   * is admin-only (identity-access-service enforces this regardless of this screen's
   * own department-scoped guard), so the action is hidden for anyone else. */
  private programHeadRoleId = signal<string | null>(null);
  readonly programHeads = signal<Map<string, ScopeHolderResponse>>(new Map());
  readonly assigningHeadFor = signal<{ programId: string; programName: string } | null>(null);
  readonly headPickerValue = signal<string>('');
  readonly savingHead = signal(false);

  readonly allYears = signal<AcademicYearResponse[]>([]);
  readonly yearFilter = signal<string>(''); // '' = All Years
  readonly assignments = signal<DegreeProgramAcademicYearResponse[]>([]);
  readonly referenceDegrees = signal<DegreeProgramResponse[]>([]);
  readonly lecturers = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  /** Row expand/collapse — which assignment's courses (with their per-year lecturer)
   * are currently shown, and what was loaded for it. */
  readonly expandedAssignmentId = signal<string | null>(null);
  readonly expandedCourses = signal<CourseAcademicYearResponse[]>([]);
  readonly expandedCoursesLoading = signal(false);

  /** Client-side filters on top of the server-side year filter. */
  readonly programFilter = signal('');
  readonly codeFilter = signal('');
  readonly durationFilter = signal('');
  readonly creditsFilter = signal('');
  readonly statusFilter = signal('');

  readonly yearOptions = computed(() =>
    this.allYears().map((y) => ({ value: y.id, label: y.name, sublabel: y.status === 'ARCHIVED' ? 'Archived' : undefined })),
  );
  readonly programOptions = computed(() =>
    uniqueOptions(this.assignments(), (a) => a.degreeProgramId, (a) => a.degreeProgramName, (a) => a.degreeProgramCode),
  );
  readonly durationOptions = computed(() =>
    numberOptions(this.assignments().map((a) => a.durationYears), (n) => `${n} year${n === 1 ? '' : 's'}`),
  );
  readonly creditOptions = computed(() => numberOptions(this.assignments().map((a) => a.totalCredits), (n) => `${n} credits`));
  readonly statusFilterOptions = statusOptions(['PLANNED', 'ACTIVE', 'DISCONTINUED']);

  readonly filteredAssignments = computed(() =>
    this.assignments().filter(
      (a) =>
        (!this.programFilter() || a.degreeProgramId === this.programFilter()) &&
        matchesText(this.codeFilter(), a.degreeProgramCode) &&
        (!this.durationFilter() || String(a.durationYears) === this.durationFilter()) &&
        (!this.creditsFilter() || String(a.totalCredits) === this.creditsFilter()) &&
        (!this.statusFilter() || a.status === this.statusFilter()),
    ),
  );
  readonly activeFilterCount = computed(() =>
    countActive(this.yearFilter(), this.programFilter(), this.codeFilter().trim(), this.durationFilter(), this.creditsFilter(), this.statusFilter()),
  );
  readonly filterSummary = computed(() => showingSummary(this.filteredAssignments().length, this.assignments().length, 'program'));

  /** Non-archived years — what "Assign to Year" and the filter's useful choices are. */
  readonly assignableYears = computed(() => this.allYears().filter((y) => y.status !== 'ARCHIVED'));

  readonly showAssignDialog = signal(false);
  readonly assignDialogYearAssignments = signal<DegreeProgramAcademicYearResponse[]>([]);
  readonly assignCourses = signal<CourseResponse[]>([]);
  readonly assignCoursesLoading = signal(false);
  readonly courseSearch = signal('');
  readonly selectedCourseIds = signal<Set<string>>(new Set());

  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  readonly assignForm = this.fb.group({
    academicYearId: ['', Validators.required],
    degreeProgramId: ['', Validators.required],
    durationYears: [null as number | null],
    totalCredits: [null as number | null],
    status: ['ACTIVE' as DegreeProgramYearStatus, Validators.required],
  });

  /** "Edit course details" — one dialog covering both this course's curriculum entry
   * (credit value, requirement, recommended placement) and its offering for this
   * specific academic year (semester, capacity, lecturer, status).
   * editingCourseEntry is the resolved ProgramCourse row — resolved by catalogCourseId
   * when the dialog opens, since the expanded course list doesn't carry it directly —
   * and sent back unchanged except for the new credit value. */
  readonly editingCourse = signal<CourseAcademicYearResponse | null>(null);
  readonly editingCourseEntry = signal<ProgramCourseResponse | null>(null);
  readonly editCourseError = signal<string | null>(null);
  readonly savingCourse = signal(false);

  readonly editCourseForm = this.fb.group({
    creditValue: [3, [Validators.required, Validators.min(1)]],
    semesterId: [''],
    capacity: [1, [Validators.required, Validators.min(1)]],
    lecturerId: ['', Validators.required],
    status: ['PUBLISHED' as CourseStatus, Validators.required],
  });

  readonly editCourseSemesterOptions = computed(() => {
    const course = this.editingCourse();
    if (!course) return [];
    const year = this.allYears().find((y) => y.id === course.academicYearId);
    return (year?.semesters ?? []).map((s) => ({ value: s.id, label: s.name }));
  });

  /** "Add Course to Curriculum" — links an existing Course Catalog subject into a
   * degree program's curriculum (a ProgramCourse row). This is distinct from actually
   * running the course in a given year (that's the existing "Assign Degree to Year" /
   * Course Management → Manage Years flow) — adding to the curriculum just registers it
   * as core/elective coursework for the program. */
  readonly showAddCourseDialog = signal<{ programId: string; programName: string } | null>(null);
  readonly catalogEntries = signal<CourseCatalogResponse[]>([]);
  readonly catalogLoading = signal(false);
  readonly addCourseExcludedIds = signal<Set<string>>(new Set());
  readonly addCourseError = signal<string | null>(null);
  readonly addingCourse = signal(false);

  readonly addCourseForm = this.fb.group({
    catalogCourseId: ['', Validators.required],
    requirementType: ['CORE' as RequirementType, Validators.required],
    recommendedYear: [1, [Validators.required, Validators.min(1)]],
    recommendedSemester: [1, [Validators.required, Validators.min(1)]],
    creditValue: [3, [Validators.required, Validators.min(1)]],
  });

  readonly catalogOptionsForAdd = computed(() =>
    this.catalogEntries()
      .filter((c) => !this.addCourseExcludedIds().has(c.id))
      .map((c) => ({ value: c.id, label: `${c.code} — ${c.name}`, sublabel: `${c.defaultCredits} credits` })),
  );

  /** "Add Assignment" — a new, draft assignment on one specific course offering (a row
   * of the expanded "Show courses" table), never directly on the degree program. */
  readonly addingAssignmentFor = signal<CourseAcademicYearResponse | null>(null);
  readonly assignmentError = signal<string | null>(null);
  readonly savingAssignment = signal(false);
  readonly assignmentBriefFile = signal<File | null>(null);

  readonly assignmentForm = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    dueDate: ['', Validators.required],
    maxMarks: [100, [Validators.required, Validators.min(1), Validators.max(1000)]],
  });

  readonly assignedReferenceIdsForDialogYear = computed(
    () => new Set(this.assignDialogYearAssignments().map((a) => a.degreeProgramId)),
  );
  readonly availableToAssign = computed(() =>
    this.referenceDegrees().filter((d) => !this.assignedReferenceIdsForDialogYear().has(d.id)),
  );
  readonly availableToAssignOptions = computed(() =>
    this.availableToAssign().map((d) => ({ value: d.id, label: `${d.code} - ${d.name}` })),
  );
  readonly lecturerOptions = computed(() => this.lecturers().map((l) => ({ value: l.id, label: l.name, sublabel: l.email })));
  readonly filteredAssignCourses = computed(() => {
    const q = this.courseSearch().trim().toLowerCase();
    const courses = this.assignCourses();
    if (!q) return courses;
    return courses.filter(
      (c) => c.catalogCourseCode.toLowerCase().includes(q) || c.catalogCourseName.toLowerCase().includes(q),
    );
  });

  ngOnInit(): void {
    this.academicStructureService.listDegreePrograms({ size: 200 }).subscribe({ next: (p) => this.referenceDegrees.set(p.content) });
    this.userService.list({ role: 'LECTURER', size: 200 }).subscribe({ next: (page) => this.lecturers.set(page.content) });
    if (this.auth.currentUser()?.role === 'ADMIN') {
      this.rolesService.roles().subscribe({
        next: (roles) => {
          this.programHeadRoleId.set(roles.find((r) => r.name === 'Program Head')?.id ?? null);
          this.loadProgramHeads();
        },
      });
    }
    this.academicStructureService.listAcademicYears().subscribe({
      next: (years) => {
        this.allYears.set(years);
        // Default to "All Years": several years can be ACTIVE at once (3-4 year degree
        // programs mean concurrently live years of study), so there's no single "the"
        // current year to pre-select — showing everything is the honest default.
        this.load();
      },
      error: () => this.load(),
    });
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const requestId = ++this.loadRequestId;
    this.academicStructureService.listDegreeProgramAcademicYears(this.yearFilter() || null).subscribe({
      next: (v) => {
        if (requestId !== this.loadRequestId) return; // a newer filter change already superseded this request
        this.assignments.set(v);
        this.loading.set(false);
        this.loadProgramHeads();
      },
      error: () => {
        if (requestId !== this.loadRequestId) return;
        this.loadError.set('Could not load degree program assignments.');
        this.loading.set(false);
      },
    });
  }

  private loadProgramHeads(): void {
    const roleId = this.programHeadRoleId();
    const programIds = [...new Set(this.assignments().map((a) => a.degreeProgramId))];
    if (!roleId || !programIds.length) return;
    this.rolesService.scopeHolders(roleId, 'DEGREE_PROGRAM', programIds).subscribe({
      next: (holders) => this.programHeads.set(new Map(holders.map((h) => [h.scopeId as string, h]))),
    });
  }

  openAssignHead(programId: string, programName: string): void {
    this.assigningHeadFor.set({ programId, programName });
    this.headPickerValue.set(this.programHeads().get(programId)?.userId ?? '');
  }

  submitHead(): void {
    const target = this.assigningHeadFor();
    const roleId = this.programHeadRoleId();
    if (!target || !roleId) return;
    this.savingHead.set(true);
    this.rolesService.setScopeHolder(roleId, 'DEGREE_PROGRAM', target.programId, this.headPickerValue() || null).subscribe({
      next: (holder) => {
        this.savingHead.set(false);
        this.assigningHeadFor.set(null);
        this.programHeads.update((map) => {
          const next = new Map(map);
          if (holder) next.set(target.programId, holder);
          else next.delete(target.programId);
          return next;
        });
        this.showToast(holder ? `${holder.userName} is now Program Head of ${target.programName}.` : `Program Head removed from ${target.programName}.`, false);
      },
      error: (err: HttpErrorResponse) => {
        this.savingHead.set(false);
        this.showToast(err.error?.message ?? 'Could not update the Program Head.', true);
      },
    });
  }

  onYearFilterChange(value: string): void {
    this.yearFilter.set(value);
    this.load();
  }

  clearFilters(): void {
    this.programFilter.set('');
    this.codeFilter.set('');
    this.durationFilter.set('');
    this.creditsFilter.set('');
    this.statusFilter.set('');
    if (this.yearFilter()) this.onYearFilterChange('');
  }

  referenceOf(programId: string): DegreeProgramResponse | undefined {
    return this.referenceDegrees().find((d) => d.id === programId);
  }

  openAssignDialog(): void {
    this.dialogError.set(null);
    const defaultYearId = this.yearFilter() || this.assignableYears()[0]?.id || '';
    this.assignForm.reset({
      academicYearId: defaultYearId,
      degreeProgramId: '',
      durationYears: null,
      totalCredits: null,
      status: 'ACTIVE',
    });
    this.assignCourses.set([]);
    this.courseSearch.set('');
    this.selectedCourseIds.set(new Set());
    this.showAssignDialog.set(true);
    if (defaultYearId) this.onAssignYearChange(defaultYearId);
  }

  onAssignYearChange(yearId: string): void {
    this.assignForm.patchValue({ academicYearId: yearId });
    if (!yearId) {
      this.assignDialogYearAssignments.set([]);
      return;
    }
    this.academicStructureService.listDegreeProgramAcademicYears(yearId).subscribe({
      next: (v) => this.assignDialogYearAssignments.set(v),
    });
  }

  onAssignProgramChange(programId: string): void {
    this.assignForm.patchValue({ degreeProgramId: programId });
    this.courseSearch.set('');
    if (!programId) {
      this.assignCourses.set([]);
      this.selectedCourseIds.set(new Set());
      return;
    }
    // Pre-fill with the reference degree's own defaults — most assignments don't need
    // an override, so showing the real numbers (editable) beats an empty placeholder.
    const reference = this.referenceDegrees().find((d) => d.id === programId);
    if (reference) {
      this.assignForm.patchValue({ durationYears: reference.durationYears, totalCredits: reference.totalCredits });
    }
    this.assignCoursesLoading.set(true);
    this.academicStructureService.listCourses(false, { degreeProgramId: programId, size: 200 }).subscribe({
      next: (p) => {
        this.assignCourses.set(p.content);
        this.selectedCourseIds.set(new Set(p.content.map((c) => c.id))); // default: all selected
        this.assignCoursesLoading.set(false);
      },
      error: () => this.assignCoursesLoading.set(false),
    });
  }

  isCourseSelected(courseId: string): boolean {
    return this.selectedCourseIds().has(courseId);
  }

  toggleCourse(courseId: string): void {
    this.selectedCourseIds.update((set) => {
      const next = new Set(set);
      if (next.has(courseId)) next.delete(courseId);
      else next.add(courseId);
      return next;
    });
  }

  selectAllCourses(): void {
    this.selectedCourseIds.set(new Set(this.assignCourses().map((c) => c.id)));
  }

  clearAllCourses(): void {
    this.selectedCourseIds.set(new Set());
  }

  submitAssign(): void {
    if (this.assignForm.invalid) {
      this.dialogError.set('Choose an academic year, a reference degree and a status.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    this.assignForm.disable();
    const { academicYearId, degreeProgramId, durationYears, totalCredits, status } = this.assignForm.getRawValue();
    this.academicStructureService
      .assignDegreeProgramToAcademicYear(degreeProgramId!, {
        academicYearId: academicYearId!,
        durationYears: durationYears ?? null,
        totalCredits: totalCredits ?? null,
        status: status!,
        courseIds: Array.from(this.selectedCourseIds()),
      })
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.assignForm.enable();
          this.showAssignDialog.set(false);
          this.showToast(`${created.degreeProgramName} assigned to ${created.academicYearName}.`, false);
          if (!this.yearFilter() || this.yearFilter() === academicYearId) this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.assignForm.enable();
          this.dialogError.set(err.error?.message ?? 'Could not assign this degree to the selected year.');
        },
      });
  }

  deleteAssignment(assignment: DegreeProgramAcademicYearResponse): void {
    if (!confirm(`Remove ${assignment.degreeProgramName} from ${assignment.academicYearName}?`)) return;
    this.academicStructureService.deleteDegreeProgramAcademicYear(assignment.degreeProgramId, assignment.id).subscribe({
      next: () => {
        this.assignments.update((list) => list.filter((a) => a.id !== assignment.id));
        this.showToast(`${assignment.degreeProgramName} removed from ${assignment.academicYearName}.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not remove this assignment.', true),
    });
  }

  toggleExpand(assignment: DegreeProgramAcademicYearResponse): void {
    if (this.expandedAssignmentId() === assignment.id) {
      this.expandedAssignmentId.set(null);
      return;
    }
    this.expandedAssignmentId.set(assignment.id);
    this.expandedCoursesLoading.set(true);
    this.academicStructureService.listCoursesForAssignment(assignment.degreeProgramId, assignment.id).subscribe({
      next: (v) => {
        this.expandedCourses.set(v);
        this.expandedCoursesLoading.set(false);
      },
      error: () => this.expandedCoursesLoading.set(false),
    });
  }

  lecturerName(lecturerId: string): string {
    const l = this.lecturers().find((x) => x.id === lecturerId);
    return l ? l.name : 'Unassigned';
  }

  /** Removes this course entirely — the course template (Course) and every academic-year
   * offering it has (CourseAcademicYear), not just this one year's row. Reused mainly to
   * clean up an accidental duplicate course created from "Create Course"/"Manage Years". */
  removeCourse(course: CourseAcademicYearResponse): void {
    if (!confirm(`Remove ${course.catalogCourseCode} — ${course.catalogCourseName} from this program? This removes it from every academic year it's assigned to.`)) return;
    this.academicStructureService.deleteCourse(course.courseId).subscribe({
      next: () => {
        this.expandedCourses.update((list) => list.filter((c) => c.id !== course.id));
        this.assignments.update((list) =>
          list.map((a) => (a.degreeProgramId === course.degreeProgramId && a.academicYearId === course.academicYearId
            ? { ...a, courseCount: Math.max(0, a.courseCount - 1) }
            : a)),
        );
        this.showToast(`${course.catalogCourseCode} removed from this program.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not remove this course.', true),
    });
  }

  /** Opens the combined "Edit course details" dialog — the per-year offering's own
   * fields (semester/capacity/lecturer/status) are already on hand from the expanded
   * row; the curriculum entry (credit value, requirement, recommended placement) isn't,
   * so it's resolved by catalogCourseId from this program's curriculum once the dialog
   * opens, and cached for submitEditCourse to send back unchanged alongside the new
   * credit value. */
  openEditCourse(course: CourseAcademicYearResponse): void {
    this.editingCourse.set(course);
    this.editingCourseEntry.set(null);
    this.editCourseError.set(null);
    this.editCourseForm.reset({
      creditValue: 3,
      semesterId: course.semesterId ?? '',
      capacity: course.capacity,
      lecturerId: course.lecturerId,
      status: course.status,
    });
    this.academicStructureService.listCurriculum(course.degreeProgramId).subscribe({
      next: (curriculum) => {
        const entry = curriculum.find((c) => c.catalogCourseId === course.catalogCourseId);
        if (entry) {
          this.editingCourseEntry.set(entry);
          this.editCourseForm.patchValue({ creditValue: entry.creditValue });
        }
      },
    });
  }

  closeEditCourseDialog(): void {
    this.editingCourse.set(null);
  }

  submitEditCourse(): void {
    const course = this.editingCourse();
    if (!course || this.editCourseForm.invalid) return;
    this.editCourseError.set(null);
    this.savingCourse.set(true);
    this.editCourseForm.disable();
    const v = this.editCourseForm.getRawValue();

    const finish = (updatedOffering: CourseAcademicYearResponse) => {
      this.expandedCourses.update((list) => list.map((c) => (c.id === updatedOffering.id ? updatedOffering : c)));
      this.savingCourse.set(false);
      this.editCourseForm.enable();
      this.editingCourse.set(null);
      this.showToast(`${updatedOffering.catalogCourseCode} updated.`, false);
    };

    this.academicStructureService
      .updateCourseAcademicYear(course.courseId, course.id, {
        semesterId: v.semesterId || null,
        capacity: v.capacity,
        lecturerId: v.lecturerId,
        status: v.status!,
      })
      .subscribe({
        next: (updatedOffering) => {
          const entry = this.editingCourseEntry();
          if (!entry) {
            finish(updatedOffering);
            return;
          }
          this.academicStructureService
            .updateCurriculumEntry(course.degreeProgramId, entry.id, {
              requirementType: entry.requirementType,
              recommendedYear: entry.recommendedYear,
              recommendedSemester: entry.recommendedSemester,
              creditValue: v.creditValue!,
            })
            .subscribe({
              next: () => finish(updatedOffering),
              error: (err: HttpErrorResponse) => {
                this.savingCourse.set(false);
                this.editCourseForm.enable();
                this.editCourseError.set(err.error?.message ?? 'The offering was updated, but the credit value could not be saved.');
              },
            });
        },
        error: (err: HttpErrorResponse) => {
          this.savingCourse.set(false);
          this.editCourseForm.enable();
          this.editCourseError.set(err.error?.message ?? 'Could not update this course.');
        },
      });
  }

  openAddCourseDialog(programId: string, programName: string): void {
    this.addCourseError.set(null);
    this.addCourseForm.reset({ catalogCourseId: '', requirementType: 'CORE', recommendedYear: 1, recommendedSemester: 1, creditValue: 3 });
    this.showAddCourseDialog.set({ programId, programName });
    this.catalogLoading.set(true);
    this.academicStructureService.listCurriculum(programId).subscribe({
      next: (curriculum) => {
        this.addCourseExcludedIds.set(new Set(curriculum.map((c) => c.catalogCourseId)));
        this.academicStructureService.listCourseCatalog({ size: 200 }).subscribe({
          next: (page) => {
            this.catalogEntries.set(page.content);
            this.catalogLoading.set(false);
          },
          error: () => this.catalogLoading.set(false),
        });
      },
      error: () => this.catalogLoading.set(false),
    });
  }

  onAddCourseCatalogChange(catalogCourseId: string): void {
    this.addCourseForm.patchValue({ catalogCourseId });
    const entry = this.catalogEntries().find((c) => c.id === catalogCourseId);
    if (entry) this.addCourseForm.patchValue({ creditValue: entry.defaultCredits });
  }

  submitAddCourse(): void {
    const target = this.showAddCourseDialog();
    if (!target || this.addCourseForm.invalid) {
      this.addCourseError.set('Choose a course and fill in every field.');
      return;
    }
    this.addCourseError.set(null);
    this.addingCourse.set(true);
    this.addCourseForm.disable();
    const { catalogCourseId, requirementType, recommendedYear, recommendedSemester, creditValue } = this.addCourseForm.getRawValue();
    this.academicStructureService
      .addCurriculumEntry(target.programId, {
        catalogCourseId: catalogCourseId!,
        requirementType: requirementType!,
        recommendedYear: recommendedYear!,
        recommendedSemester: recommendedSemester!,
        creditValue: creditValue!,
      })
      .subscribe({
        next: (added) => {
          this.addingCourse.set(false);
          this.addCourseForm.enable();
          this.showAddCourseDialog.set(null);
          this.showToast(`${added.catalogCourseCode} added to ${target.programName}'s curriculum.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.addingCourse.set(false);
          this.addCourseForm.enable();
          this.addCourseError.set(err.error?.message ?? 'Could not add this course to the curriculum.');
        },
      });
  }

  closeAddCourseDialog(): void {
    this.showAddCourseDialog.set(null);
  }

  openAddAssignment(course: CourseAcademicYearResponse): void {
    const due = new Date(Date.now() + 7 * 86400000);
    due.setHours(23, 59, 0, 0);
    this.assignmentForm.reset({ title: '', description: '', dueDate: toLocalInput(due), maxMarks: 100 });
    this.assignmentError.set(null);
    this.assignmentBriefFile.set(null);
    this.addingAssignmentFor.set(course);
  }

  pickAssignmentBrief(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.assignmentBriefFile.set(file);
  }

  clearAssignmentBrief(input: HTMLInputElement): void {
    input.value = '';
    this.assignmentBriefFile.set(null);
  }

  submitAssignment(): void {
    const course = this.addingAssignmentFor();
    if (!course || this.assignmentForm.invalid) {
      this.assignmentError.set('Enter a title, due date and maximum marks.');
      return;
    }
    this.assignmentError.set(null);
    this.savingAssignment.set(true);
    this.assignmentForm.disable();
    const v = this.assignmentForm.getRawValue();
    const brief = this.assignmentBriefFile();
    this.gradingService
      .create({
        offeringId: course.id,
        title: v.title ?? '',
        description: v.description || null,
        dueDate: new Date(v.dueDate ?? '').toISOString(),
        maxMarks: Number(v.maxMarks),
      })
      .subscribe({
        next: (saved) => {
          const finish = (message: string) => {
            this.savingAssignment.set(false);
            this.assignmentForm.enable();
            this.addingAssignmentFor.set(null);
            this.showToast(message, false);
          };
          const createdMsg = `"${saved.title}" created as a draft for ${course.catalogCourseCode} — publish it from Assignments when ready.`;
          if (brief) {
            this.gradingService.uploadBrief(saved.id, brief).subscribe({
              next: () => finish(`${createdMsg} Brief attached.`),
              error: () => finish(`${createdMsg} Could not attach the brief — add it from the Assignments screen.`),
            });
          } else {
            finish(createdMsg);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.savingAssignment.set(false);
          this.assignmentForm.enable();
          this.assignmentError.set(err.error?.message ?? 'Could not create this assignment.');
        },
      });
  }

  closeAddAssignmentDialog(): void {
    this.addingAssignmentFor.set(null);
    this.assignmentBriefFile.set(null);
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
