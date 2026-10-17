import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AcademicYearResponse,
  CourseAcademicYearResponse,
  CourseResponse,
  CourseStatus,
} from '../../core/models/academic-structure.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';

/**
 * "My Courses" — the courses a lecturer is assigned, independent of academic year
 * (courses are reusable templates now). "Manage Years" assigns one to the academic
 * years it actually runs in — the same template can run across many years.
 */
@Component({
  selector: 'app-lecturer-courses',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './lecturer-courses.component.html',
})
export class LecturerCoursesComponent {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);

  readonly courses = signal<CourseResponse[]>([]);
  readonly academicYears = signal<AcademicYearResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

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
    this.academicStructureService.listAcademicYears().subscribe({ next: (v) => this.academicYears.set(v) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.academicStructureService.listCourses(true).subscribe({
      next: (v) => {
        this.courses.set(v);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load your courses.');
        this.loading.set(false);
      },
    });
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
