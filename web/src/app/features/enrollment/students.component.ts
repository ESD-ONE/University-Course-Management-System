import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { CourseEnrollmentResponse, MyStudentResponse } from '../../core/models/enrollment.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { errorMessage, initials, saveBlob, Toaster } from '../../shared/ui';

/**
 * Wireframe lecturerStudents + lecturerRoster in one screen. "All my courses" lists every
 * student across the lecturer's offerings; choosing one course shows that offering's
 * roster — enrolled and waitlisted — with capacity and core/elective counts.
 */
@Component({
  selector: 'app-students',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './students.component.html',
})
export class StudentsComponent {
  private enrollment = inject(EnrollmentService);
  private academic = inject(AcademicStructureService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly auth = inject(AuthService);

  readonly offerings = signal<CourseOfferingResponse[]>([]);
  readonly offeringId = signal<string>(this.route.snapshot.queryParamMap.get('offering') ?? '');
  readonly allStudents = signal<MyStudentResponse[]>([]);
  readonly roster = signal<CourseEnrollmentResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly search = signal('');
  readonly programFilter = signal('');
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));
  readonly initials = initials;

  readonly selectedOffering = computed(() => this.offerings().find((o) => o.id === this.offeringId()) ?? null);
  readonly programs = computed(() => {
    const names = this.offeringId()
      ? this.roster().map((r) => r.studentProgramName)
      : this.allStudents().map((s) => s.programName);
    return [...new Set(names.filter((n): n is string => !!n))];
  });
  readonly filteredAll = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.allStudents().filter(
      (s) =>
        (!this.programFilter() || s.programName === this.programFilter()) &&
        (!q || (s.name ?? '').toLowerCase().includes(q) || (s.studentNumber ?? '').toLowerCase().includes(q)),
    );
  });
  readonly filteredRoster = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.roster().filter(
      (r) =>
        (!this.programFilter() || r.studentProgramName === this.programFilter()) &&
        (!q || (r.studentName ?? '').toLowerCase().includes(q) || (r.studentNumber ?? '').toLowerCase().includes(q)),
    );
  });
  readonly enrolledCount = computed(() => this.roster().filter((r) => r.status === 'ENROLLED').length);
  readonly waitlistCount = computed(() => this.roster().filter((r) => r.status === 'WAITLISTED').length);
  readonly coreCount = computed(() => this.roster().filter((r) => r.status === 'ENROLLED' && r.requirementType === 'CORE').length);

  constructor() {
    const isAdmin = this.auth.currentUser()?.role === 'ADMIN';
    this.academic.listCourseOfferings({ mine: !isAdmin }).subscribe({
      next: (v) => this.offerings.set(v.filter((o) => o.academicYearStatus !== 'ARCHIVED')),
    });
    this.load();
  }

  selectOffering(id: string): void {
    this.offeringId.set(id);
    this.programFilter.set('');
    this.router.navigate([], { queryParams: { offering: id || null }, replaceUrl: true });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    const id = this.offeringId();
    if (id) {
      this.enrollment.roster(id).subscribe({
        next: (v) => {
          this.roster.set(v);
          this.loading.set(false);
        },
        error: (err) => {
          this.loadError.set(errorMessage(err, 'Could not load this roster.'));
          this.loading.set(false);
        },
      });
    } else {
      this.enrollment.myStudents().subscribe({
        next: (v) => {
          this.allStudents.set(v);
          this.loading.set(false);
        },
        error: (err) => {
          this.loadError.set(errorMessage(err, 'Could not load your students.'));
          this.loading.set(false);
        },
      });
    }
  }

  remove(row: CourseEnrollmentResponse): void {
    if (!confirm(`Remove ${row.studentName} from ${row.courseCode}?`)) return;
    this.enrollment.drop(row.id).subscribe({
      next: () => {
        this.toaster.show(`${row.studentName} removed from ${row.courseCode}.`);
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not remove this student.'), true),
    });
  }

  exportCsv(): void {
    const header = ['Student', 'Student ID', 'Email', 'Program', 'Year', 'Enrolled On', 'Requirement', 'Status'];
    const rows = this.filteredRoster().map((r) => [
      r.studentName ?? '', r.studentNumber ?? '', r.studentEmail ?? '', r.studentProgramName ?? '',
      r.studentYearOfStudy?.toString() ?? '', r.enrolledOn, r.requirementType ?? '', r.status,
    ]);
    const cell = (c: string) => {
      const safe = /^[=+\-@]/.test(c) ? `'${c}` : c;
      return `"${safe.replace(/"/g, '""')}"`;
    };
    const csv = [header, ...rows].map((cols) => cols.map(cell).join(',')).join('\r\n');
    saveBlob(new Blob(['﻿' + csv], { type: 'text/csv' }), `${this.selectedOffering()?.code ?? 'roster'}-roster.csv`);
  }
}
