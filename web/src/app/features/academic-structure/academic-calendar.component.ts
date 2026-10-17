import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AcademicYearResponse,
  AcademicYearStatus,
  SemesterResponse,
  SemesterStatus,
} from '../../core/models/academic-structure.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';

/** Matches html-wireframes/index.html's adminCalendar() / newYear() / addSemester(),
 * extended with full CRUD (edit/delete) on both years and semesters — including
 * managing a year's status directly, not just "activate". */
@Component({
  selector: 'app-academic-calendar',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './academic-calendar.component.html',
})
export class AcademicCalendarComponent implements OnInit {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);

  readonly years = signal<AcademicYearResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly selectedYearId = signal<string | null>(null);

  readonly selectedYear = computed(
    () => this.years().find((y) => y.id === this.selectedYearId()) ?? this.years().at(0) ?? null,
  );

  readonly showYearDialog = signal(false);
  readonly editingYearId = signal<string | null>(null);
  readonly showSemesterDialog = signal(false);
  readonly editingSemesterId = signal<string | null>(null);
  /** The edit dialog doesn't expose enrollment-window fields; preserved here so
   * updateSemester() doesn't null them out on a save. */
  private editingSemesterEnrollment: { openAt: string | null; closeAt: string | null } = { openAt: null, closeAt: null };
  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);

  readonly yearForm = this.fb.group({
    name: ['', Validators.required],
    startDate: [''],
    endDate: [''],
    status: ['UPCOMING' as AcademicYearStatus, Validators.required],
    createDefaultSemesters: [true],
  });

  readonly semesterForm = this.fb.group({
    name: ['', Validators.required],
    sequence: [1, [Validators.required, Validators.min(1)]],
    status: ['UPCOMING' as SemesterStatus, Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.academicStructureService.listAcademicYears().subscribe({
      next: (years) => {
        this.years.set(years);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load the academic calendar.');
        this.loading.set(false);
      },
    });
  }

  selectYear(id: string): void {
    this.selectedYearId.set(id);
  }

  openYearDialog(): void {
    this.editingYearId.set(null);
    this.dialogError.set(null);
    this.yearForm.reset({ name: '', startDate: '', endDate: '', status: 'UPCOMING', createDefaultSemesters: true });
    this.showYearDialog.set(true);
  }

  openEditYearDialog(year: AcademicYearResponse): void {
    this.editingYearId.set(year.id);
    this.dialogError.set(null);
    this.yearForm.reset({
      name: year.name,
      startDate: year.startDate ?? '',
      endDate: year.endDate ?? '',
      status: year.status,
      createDefaultSemesters: false,
    });
    this.showYearDialog.set(true);
  }

  submitYear(): void {
    if (this.yearForm.invalid) {
      this.dialogError.set('Enter a name for the academic year.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    const { name, startDate, endDate, status, createDefaultSemesters } = this.yearForm.getRawValue();
    const editingId = this.editingYearId();

    if (editingId) {
      this.academicStructureService
        .updateAcademicYear(editingId, { name: name!, startDate: startDate || null, endDate: endDate || null, status: status! })
        .subscribe({
          next: (updated) => {
            this.years.update((list) => list.map((y) => (y.id === updated.id ? { ...updated, semesters: y.semesters } : y)));
            this.submitting.set(false);
            this.showYearDialog.set(false);
            this.showToast(`"${updated.name}" updated.`, false);
            this.load();
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.dialogError.set(err.error?.message ?? 'Could not update the academic year.');
          },
        });
      return;
    }

    this.academicStructureService
      .createAcademicYear({
        name: name!,
        startDate: startDate || null,
        endDate: endDate || null,
        status: status!,
        createDefaultSemesters: !!createDefaultSemesters,
      })
      .subscribe({
        next: (created) => {
          this.years.update((list) => [created, ...list]);
          this.selectedYearId.set(created.id);
          this.submitting.set(false);
          this.showYearDialog.set(false);
          this.showToast(`Academic year "${created.name}" created.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not create the academic year.');
        },
      });
  }

  deleteYear(year: AcademicYearResponse): void {
    if (!confirm(`Delete academic year "${year.name}"? This cannot be undone.`)) return;
    this.academicStructureService.deleteAcademicYear(year.id).subscribe({
      next: () => {
        this.years.update((list) => list.filter((y) => y.id !== year.id));
        if (this.selectedYearId() === year.id) this.selectedYearId.set(null);
        this.showToast(`"${year.name}" deleted.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this academic year.', true),
    });
  }

  openSemesterDialog(): void {
    this.editingSemesterId.set(null);
    this.dialogError.set(null);
    const nextSeq = (this.selectedYear()?.semesters.length ?? 0) + 1;
    this.semesterForm.reset({ name: '', sequence: nextSeq, status: 'UPCOMING' });
    this.showSemesterDialog.set(true);
  }

  openEditSemesterDialog(semester: SemesterResponse): void {
    this.editingSemesterId.set(semester.id);
    this.editingSemesterEnrollment = { openAt: semester.enrollmentOpenAt, closeAt: semester.enrollmentCloseAt };
    this.dialogError.set(null);
    this.semesterForm.reset({ name: semester.name, sequence: semester.sequence, status: semester.status });
    this.showSemesterDialog.set(true);
  }

  submitSemester(): void {
    const year = this.selectedYear();
    if (!year) return;
    if (this.semesterForm.invalid) {
      this.dialogError.set('Enter a semester name and sequence.');
      return;
    }
    this.dialogError.set(null);
    this.submitting.set(true);
    const { name, sequence, status } = this.semesterForm.getRawValue();
    const editingId = this.editingSemesterId();

    if (editingId) {
      this.academicStructureService
        .updateSemester(year.id, editingId, {
          name: name!,
          sequence: sequence!,
          status: status!,
          enrollmentOpenAt: this.editingSemesterEnrollment.openAt,
          enrollmentCloseAt: this.editingSemesterEnrollment.closeAt,
        })
        .subscribe({
          next: (updated) => {
            this.years.update((list) =>
              list.map((y) => (y.id === year.id ? { ...y, semesters: y.semesters.map((s) => (s.id === updated.id ? updated : s)) } : y)),
            );
            this.submitting.set(false);
            this.showSemesterDialog.set(false);
            this.showToast(`"${updated.name}" updated.`, false);
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.dialogError.set(err.error?.message ?? 'Could not update the semester.');
          },
        });
      return;
    }

    this.academicStructureService
      .addSemester(year.id, { name: name!, sequence: sequence!, status: status! })
      .subscribe({
        next: (created) => {
          this.years.update((list) =>
            list.map((y) => (y.id === year.id ? { ...y, semesters: [...y.semesters, created] } : y)),
          );
          this.submitting.set(false);
          this.showSemesterDialog.set(false);
          this.showToast(`"${created.name}" added to ${year.name}.`, false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.dialogError.set(err.error?.message ?? 'Could not add the semester.');
        },
      });
  }

  deleteSemester(semester: SemesterResponse): void {
    const year = this.selectedYear();
    if (!year) return;
    if (!confirm(`Delete "${semester.name}"? This cannot be undone.`)) return;
    this.academicStructureService.deleteSemester(year.id, semester.id).subscribe({
      next: () => {
        this.years.update((list) =>
          list.map((y) => (y.id === year.id ? { ...y, semesters: y.semesters.filter((s) => s.id !== semester.id) } : y)),
        );
        this.showToast(`"${semester.name}" deleted.`, false);
      },
      error: (err: HttpErrorResponse) => this.showToast(err.error?.message ?? 'Could not delete this semester.', true),
    });
  }

  private showToast(message: string, error: boolean): void {
    this.toast.set({ message, error });
    setTimeout(() => this.toast.set(null), 3000);
  }
}
