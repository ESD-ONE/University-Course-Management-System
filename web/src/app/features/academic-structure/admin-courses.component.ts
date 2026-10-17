import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseResponse,
  CourseStatus,
  DegreeProgramResponse,
} from '../../core/models/academic-structure.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';

/**
 * University-wide course oversight. Courses are reusable templates (not tied to one
 * academic year), so the admin filter here is by Degree Program — there can be many
 * degrees, each with many courses, and this lets the admin narrow the list clearly.
 * "Manage Years" assigns a template to the academic years it actually runs in.
 */
@Component({
  selector: 'app-admin-courses',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, SearchableSelectComponent],
  templateUrl: './admin-courses.component.html',
})
export class AdminCoursesComponent {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);
  private loadRequestId = 0;

  readonly courses = signal<CourseResponse[]>([]);
  readonly degreePrograms = signal<DegreeProgramResponse[]>([]);
  readonly academicYears = signal<AcademicYearResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly degreeProgramFilter = signal<string>('');

  readonly degreeProgramFilterOptions = computed(() => [
    { value: '', label: 'All Degree Programs' },
    ...this.degreePrograms().map((d) => ({ value: d.id, label: `${d.code} - ${d.name}` })),
  ]);

  readonly editingCourse = signal<CourseResponse | null>(null);
  readonly managingCourse = signal<CourseResponse | null>(null);
  readonly yearAssignments = signal<CourseAcademicYearResponse[]>([]);
  readonly yearAssignmentsLoading = signal(false);
  readonly editingAssignmentId = signal<string | null>(null);
  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  readonly editCourseForm = this.fb.group({
    beginDate: ['', Validators.required],
    yearOfStudy: [1, [Validators.required, Validators.min(1)]],
    capacity: [40, [Validators.required, Validators.min(1)]],
    status: ['DRAFT' as CourseStatus, Validators.required],
  });

  readonly assignForm = this.fb.group({
    academicYearId: ['', Validators.required],
    semesterId: [''],
    capacity: [null as number | null],
    status: ['PUBLISHED' as CourseStatus, Validators.required],
  });

  constructor() {
    this.academicStructureService.listDegreePrograms().subscribe({ next: (v) => this.degreePrograms.set(v) });
    this.academicStructureService.listAcademicYears().subscribe({ next: (v) => this.academicYears.set(v) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const requestId = ++this.loadRequestId;
    this.academicStructureService.listCourses(false, this.degreeProgramFilter() || null).subscribe({
      next: (v) => {
        if (requestId !== this.loadRequestId) return; // a newer filter change already superseded this request
        this.courses.set(v);
        this.loading.set(false);
      },
      error: () => {
        if (requestId !== this.loadRequestId) return;
        this.loadError.set('Could not load courses.');
        this.loading.set(false);
      },
    });
  }

  onDegreeProgramFilterChange(value: string): void {
    this.degreeProgramFilter.set(value);
    this.load();
  }

  semestersFor(yearId: string) {
    return this.academicYears().find((y) => y.id === yearId)?.semesters ?? [];
  }

  openEditCourse(course: CourseResponse): void {
    this.editingCourse.set(course);
    this.dialogError.set(null);
    this.editCourseForm.reset({
      beginDate: course.beginDate,
      yearOfStudy: course.yearOfStudy,
      capacity: course.capacity,
      status: course.status as CourseStatus,
    });
  }

  submitEditCourse(): void {
    const course = this.editingCourse();
    if (!course || this.editCourseForm.invalid) {
      this.dialogError.set('Fill in the begin date, year of study and capacity.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    const { beginDate, yearOfStudy, capacity, status } = this.editCourseForm.getRawValue();
    this.academicStructureService
      .updateCourse(course.id, { beginDate: beginDate!, yearOfStudy: yearOfStudy!, capacity: capacity!, status: status! })
      .subscribe({
        next: (updated) => {
          this.courses.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
          this.submitting.set(false);
          this.editingCourse.set(null);
          this.showToast(`${updated.catalogCourseCode} updated.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not update this course.');
        },
      });
  }

  deleteCourse(course: CourseResponse): void {
    if (!confirm(`Delete course "${course.catalogCourseName}"? This also removes its academic-year assignments.`)) return;
    this.academicStructureService.deleteCourse(course.id).subscribe({
      next: () => {
        this.courses.update((list) => list.filter((c) => c.id !== course.id));
        this.showToast(`${course.catalogCourseCode} deleted.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this course.', true),
    });
  }

  openManageYears(course: CourseResponse): void {
    this.managingCourse.set(course);
    this.dialogError.set(null);
    this.editingAssignmentId.set(null);
    this.assignForm.reset({
      academicYearId: this.academicYears()[0]?.id ?? '',
      semesterId: '',
      capacity: null,
      status: 'PUBLISHED',
    });
    this.yearAssignmentsLoading.set(true);
    this.academicStructureService.listCourseAcademicYears(course.id).subscribe({
      next: (v) => {
        this.yearAssignments.set(v);
        this.yearAssignmentsLoading.set(false);
      },
      error: () => this.yearAssignmentsLoading.set(false),
    });
  }

  closeManageYears(): void {
    this.managingCourse.set(null);
  }

  openEditYearAssignment(assignment: CourseAcademicYearResponse): void {
    this.editingAssignmentId.set(assignment.id);
    this.dialogError.set(null);
    this.assignForm.reset({
      academicYearId: assignment.academicYearId,
      semesterId: assignment.semesterId ?? '',
      capacity: assignment.capacity,
      status: assignment.status as CourseStatus,
    });
  }

  cancelEditYearAssignment(): void {
    this.editingAssignmentId.set(null);
    this.assignForm.reset({ academicYearId: this.academicYears()[0]?.id ?? '', semesterId: '', capacity: null, status: 'PUBLISHED' });
  }

  submitYearAssignment(): void {
    const course = this.managingCourse();
    if (!course) return;
    if (this.assignForm.invalid) {
      this.dialogError.set('Choose an academic year and a status.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    const { semesterId, capacity, status } = this.assignForm.getRawValue();
    const editingId = this.editingAssignmentId();

    if (editingId) {
      this.academicStructureService
        .updateCourseAcademicYear(course.id, editingId, { semesterId: semesterId || null, capacity: capacity ?? null, status: status! })
        .subscribe({
          next: (updated) => {
            this.yearAssignments.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
            this.submitting.set(false);
            this.editingAssignmentId.set(null);
            this.showToast(`Updated assignment for ${updated.academicYearName}.`, false);
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.dialogError.set(err.error?.message ?? 'Could not update this assignment.');
          },
        });
      return;
    }

    const { academicYearId } = this.assignForm.getRawValue();
    this.academicStructureService
      .assignCourseToAcademicYear(course.id, {
        academicYearId: academicYearId!,
        semesterId: semesterId || null,
        capacity: capacity ?? null,
        status: status!,
      })
      .subscribe({
        next: (created) => {
          this.yearAssignments.update((list) => [...list, created]);
          this.submitting.set(false);
          this.showToast(`Assigned to ${created.academicYearName}.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not assign this course to the selected year.');
        },
      });
  }

  deleteYearAssignment(assignment: CourseAcademicYearResponse): void {
    const course = this.managingCourse();
    if (!course) return;
    if (!confirm(`Remove this course from ${assignment.academicYearName}?`)) return;
    this.academicStructureService.deleteCourseAcademicYear(course.id, assignment.id).subscribe({
      next: () => {
        this.yearAssignments.update((list) => list.filter((a) => a.id !== assignment.id));
        this.showToast(`Removed from ${assignment.academicYearName}.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not remove this assignment.', true),
    });
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
