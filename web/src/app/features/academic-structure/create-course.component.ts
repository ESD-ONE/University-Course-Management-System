import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CourseCatalogResponse, CourseStatus, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { SearchableSelectComponent } from '../../shared/searchable-select.component';

/**
 * Courses are templates, not academic-year-scoped: only a begin date and the year of
 * study within the degree program are captured here. The same course is assigned to
 * whichever academic years it runs in afterward, from Manage Years. No lecturer is
 * picked here — the same course can be taught by a different lecturer each year it
 * runs, so that's assigned per academic year from "Degree Programs" (see
 * CourseAcademicYear.lecturerId), not fixed at the template level.
 *
 * The subject (code/name) is typed directly rather than picked from a separate catalog
 * screen: typing a code that already exists in the catalog reuses that subject (name
 * is then read-only, since it's shared across every course using it); typing a new
 * code creates the catalog subject inline as part of saving.
 */
@Component({
  selector: 'app-create-course',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, SearchableSelectComponent],
  templateUrl: './create-course.component.html',
})
export class CreateCourseComponent implements OnInit {
  private fb = inject(FormBuilder);
  private academicStructureService = inject(AcademicStructureService);
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly backLink = computed(() => (this.isAdmin() ? '/app/courses' : '/app/my-courses'));
  readonly backLabel = computed(() => (this.isAdmin() ? 'Courses' : 'My Courses'));

  readonly catalog = signal<CourseCatalogResponse[]>([]);
  readonly degreePrograms = signal<DegreeProgramResponse[]>([]);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly loadingOptions = signal(true);

  readonly degreeProgramOptions = computed(() =>
    this.degreePrograms().map((d) => ({ value: d.id, label: `${d.code} - ${d.name}` })),
  );

  /** Set once the typed code exactly matches an existing catalog subject — the Name
   * and Credits fields lock to that subject's values instead of defining a new one. */
  readonly matchedCatalog = signal<CourseCatalogResponse | null>(null);

  readonly form = this.fb.group({
    courseCode: ['', Validators.required],
    courseName: ['', Validators.required],
    defaultCredits: [3, [Validators.required, Validators.min(1)]],
    degreeProgramId: ['', Validators.required],
    beginDate: ['', Validators.required],
    yearOfStudy: [1, [Validators.required, Validators.min(1)]],
    capacity: [40, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.academicStructureService.listCourseCatalog().subscribe({ next: (v) => this.catalog.set(v) });
    this.academicStructureService.listDegreePrograms().subscribe({
      next: (v) => {
        this.degreePrograms.set(v);
        if (v.length) this.form.patchValue({ degreeProgramId: v[0].id });
        this.loadingOptions.set(false);
      },
      error: () => this.loadingOptions.set(false),
    });
  }

  onCodeInput(value: string): void {
    const match = this.catalog().find((c) => c.code.toLowerCase() === value.trim().toLowerCase()) ?? null;
    this.matchedCatalog.set(match);
    if (match) {
      this.form.patchValue({ courseName: match.name, defaultCredits: match.defaultCredits });
    }
  }

  save(publish: boolean): void {
    if (this.form.invalid) {
      this.error.set('Fill in the course code, name, degree program, begin date and year of study.');
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    const { courseCode, courseName, defaultCredits, degreeProgramId, beginDate, yearOfStudy, capacity } =
      this.form.getRawValue();
    const status: CourseStatus = publish ? 'PUBLISHED' : 'DRAFT';

    const createCourse = (catalogCourseId: string) => {
      this.academicStructureService
        .createCourse({
          catalogCourseId,
          degreeProgramId: degreeProgramId!,
          beginDate: beginDate!,
          yearOfStudy: yearOfStudy!,
          capacity: capacity!,
          status,
        })
        .subscribe({
          next: () => {
            this.submitting.set(false);
            this.router.navigateByUrl(this.backLink());
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? 'Could not create this course.');
          },
        });
    };

    const existing = this.matchedCatalog();
    if (existing) {
      createCourse(existing.id);
      return;
    }

    const program = this.degreePrograms().find((p) => p.id === degreeProgramId);
    this.academicStructureService
      .createCourseCatalogEntry({
        code: courseCode!.toUpperCase(),
        name: courseName!,
        defaultCredits: defaultCredits!,
        owningDepartmentId: program!.departmentId,
      })
      .subscribe({
        next: (catalogEntry) => {
          this.catalog.update((list) => [...list, catalogEntry]);
          createCourse(catalogEntry.id);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.error.set(err.error?.message ?? 'Could not create this subject.');
        },
      });
  }
}
