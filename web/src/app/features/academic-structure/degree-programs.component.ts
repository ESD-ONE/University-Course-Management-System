import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseResponse,
  DegreeProgramAcademicYearResponse,
  DegreeProgramResponse,
  DegreeProgramYearStatus,
} from '../../core/models/academic-structure.model';
import { UserResponse } from '../../core/models/user.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { UserService } from '../../core/services/user.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';

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
  imports: [CommonModule, ReactiveFormsModule, FormsModule, SearchableSelectComponent],
  templateUrl: './degree-programs.component.html',
})
export class DegreeProgramsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);
  private userService = inject(UserService);
  private loadRequestId = 0;

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
  readonly editingCourseLecturerId = signal<string | null>(null);
  readonly lecturerEditValue = signal<string>('');

  /** Non-archived years — what "Assign to Year" and the filter's useful choices are. */
  readonly assignableYears = computed(() => this.allYears().filter((y) => y.status !== 'ARCHIVED'));

  readonly showAssignDialog = signal(false);
  readonly assignDialogYearAssignments = signal<DegreeProgramAcademicYearResponse[]>([]);
  readonly assignCourses = signal<CourseResponse[]>([]);
  readonly assignCoursesLoading = signal(false);
  readonly courseSearch = signal('');
  readonly selectedCourseIds = signal<Set<string>>(new Set());

  readonly editingAssignment = signal<DegreeProgramAcademicYearResponse | null>(null);
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

  readonly editForm = this.fb.group({
    durationYears: [null as number | null],
    totalCredits: [null as number | null],
    status: ['ACTIVE' as DegreeProgramYearStatus, Validators.required],
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
    this.academicStructureService.listDegreePrograms().subscribe({ next: (v) => this.referenceDegrees.set(v) });
    this.userService.list({ role: 'LECTURER', size: 200 }).subscribe({ next: (page) => this.lecturers.set(page.content) });
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
      },
      error: () => {
        if (requestId !== this.loadRequestId) return;
        this.loadError.set('Could not load degree program assignments.');
        this.loading.set(false);
      },
    });
  }

  onYearFilterChange(value: string): void {
    this.yearFilter.set(value);
    this.load();
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
    this.academicStructureService.listCourses(false, programId).subscribe({
      next: (courses) => {
        this.assignCourses.set(courses);
        this.selectedCourseIds.set(new Set(courses.map((c) => c.id))); // default: all selected
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
          this.showAssignDialog.set(false);
          this.showToast(`${created.degreeProgramName} assigned to ${created.academicYearName}.`, false);
          if (!this.yearFilter() || this.yearFilter() === academicYearId) this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not assign this degree to the selected year.');
        },
      });
  }

  openEditDialog(assignment: DegreeProgramAcademicYearResponse): void {
    this.editingAssignment.set(assignment);
    this.dialogError.set(null);
    this.editForm.reset({
      durationYears: assignment.durationYears,
      totalCredits: assignment.totalCredits,
      status: assignment.status as DegreeProgramYearStatus,
    });
  }

  submitEdit(): void {
    const assignment = this.editingAssignment();
    if (!assignment || this.editForm.invalid) return;
    this.dialogError.set(null);
    this.submitting.set(true);
    const { durationYears, totalCredits, status } = this.editForm.getRawValue();
    this.academicStructureService
      .updateDegreeProgramAcademicYear(assignment.degreeProgramId, assignment.id, {
        durationYears: durationYears ?? null,
        totalCredits: totalCredits ?? null,
        status: status!,
      })
      .subscribe({
        next: (updated) => {
          this.assignments.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
          this.submitting.set(false);
          this.editingAssignment.set(null);
          this.showToast(`${updated.degreeProgramName} updated.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not update this assignment.');
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
    this.editingCourseLecturerId.set(null);
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

  openEditCourseLecturer(course: CourseAcademicYearResponse): void {
    this.editingCourseLecturerId.set(course.id);
    this.lecturerEditValue.set(course.lecturerId);
  }

  cancelEditCourseLecturer(): void {
    this.editingCourseLecturerId.set(null);
  }

  submitCourseLecturer(course: CourseAcademicYearResponse): void {
    const lecturerId = this.lecturerEditValue();
    this.academicStructureService
      .updateCourseAcademicYear(course.courseId, course.id, {
        semesterId: course.semesterId,
        capacity: course.capacity,
        lecturerId,
        status: course.status,
      })
      .subscribe({
        next: (updated) => {
          this.expandedCourses.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
          this.editingCourseLecturerId.set(null);
          this.showToast(`${updated.catalogCourseCode} now taught by ${this.lecturerName(updated.lecturerId)}.`, false);
        },
        error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not reassign the lecturer.', true),
      });
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
