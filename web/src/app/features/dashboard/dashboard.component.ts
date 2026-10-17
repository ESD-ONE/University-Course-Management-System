import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AcademicYearResponse, CourseResponse, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import {
  CourseCategoryResponse,
  DepartmentResponse,
  FacultyResponse,
  GradingScaleResponse,
} from '../../core/models/reference-data.model';
import { UserSummary } from '../../core/models/user.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { UserService } from '../../core/services/user.service';

interface ChartSegment {
  label: string;
  value: number;
  color: string;
}

interface BarDatum {
  label: string;
  value: number;
}

interface ReferenceDataSummary {
  label: string;
  total: number;
  active: number;
  inactive: number;
}

/**
 * Role-aware overview: admin sees institution-wide stats (users, academic years,
 * degrees, courses, and every Reference Data type's active/inactive breakdown) with
 * CSS-only charts (donut + bar — no chart library dependency, see styles.scss's
 * ".donut-chart"/".bar-chart-*" rules); lecturer sees their own teaching load.
 * Everything here is read from already-existing endpoints — no new backend
 * aggregation was needed. Academic years are NOT mutually exclusive — degree programs
 * run 3-4 years, so several years can be concurrently ACTIVE (see
 * AcademicCalendarService's Javadoc) — this shows a count, not a single "the" year.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  private userService = inject(UserService);
  private academicStructureService = inject(AcademicStructureService);
  private referenceDataService = inject(ReferenceDataService);
  readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly loading = signal(true);

  readonly userSummary = signal<UserSummary | null>(null);
  readonly academicYears = signal<AcademicYearResponse[]>([]);
  readonly degreePrograms = signal<DegreeProgramResponse[]>([]);
  readonly courses = signal<CourseResponse[]>([]);
  readonly faculties = signal<FacultyResponse[]>([]);
  readonly departments = signal<DepartmentResponse[]>([]);
  readonly gradingScales = signal<GradingScaleResponse[]>([]);
  readonly courseCategories = signal<CourseCategoryResponse[]>([]);

  readonly activeYears = computed(() => this.academicYears().filter((y) => y.status === 'ACTIVE'));
  readonly activeYearNames = computed(() => this.activeYears().map((y) => y.name).join(', '));

  readonly publishedCount = computed(() => this.courses().filter((c) => c.status === 'PUBLISHED').length);
  readonly draftCount = computed(() => this.courses().filter((c) => c.status === 'DRAFT').length);

  readonly courseStatusSegments = computed<ChartSegment[]>(() => [
    { label: 'Published', value: this.publishedCount(), color: 'var(--success)' },
    { label: 'Draft', value: this.draftCount(), color: 'var(--ink-faint)' },
  ]);

  readonly userRoleSegments = computed<ChartSegment[]>(() => {
    const s = this.userSummary();
    if (!s) return [];
    return [
      { label: 'Students', value: s.students, color: 'var(--accent)' },
      { label: 'Staff', value: s.staff, color: 'var(--gold)' },
    ];
  });

  readonly coursesPerProgram = computed<BarDatum[]>(() => {
    const counts = new Map<string, number>();
    for (const c of this.courses()) {
      counts.set(c.degreeProgramCode, (counts.get(c.degreeProgramCode) ?? 0) + 1);
    }
    return Array.from(counts.entries())
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 6);
  });

  readonly maxBarValue = computed(() => Math.max(1, ...this.coursesPerProgram().map((d) => d.value)));

  private summarize(label: string, items: { status: string }[]): ReferenceDataSummary {
    const active = items.filter((i) => i.status === 'ACTIVE').length;
    return { label, total: items.length, active, inactive: items.length - active };
  }
  readonly referenceDataSummaries = computed<ReferenceDataSummary[]>(() => [
    this.summarize('Faculties', this.faculties()),
    this.summarize('Departments', this.departments()),
    this.summarize('Grading Scales', this.gradingScales()),
    this.summarize('Course Categories', this.courseCategories()),
  ]);

  ngOnInit(): void {
    this.loading.set(true);
    this.academicStructureService.listDegreePrograms().subscribe({ next: (v) => this.degreePrograms.set(v) });
    this.academicStructureService.listAcademicYears().subscribe({ next: (v) => this.academicYears.set(v) });

    const mine = !this.isAdmin();
    this.academicStructureService.listCourses(mine).subscribe({
      next: (v) => {
        this.courses.set(v);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    if (this.isAdmin()) {
      this.userService.summary().subscribe({ next: (v) => this.userSummary.set(v) });
      this.referenceDataService.listFaculties().subscribe({ next: (v) => this.faculties.set(v) });
      this.referenceDataService.listDepartments().subscribe({ next: (v) => this.departments.set(v) });
      this.referenceDataService.listGradingScales().subscribe({ next: (v) => this.gradingScales.set(v) });
      this.referenceDataService.listCourseCategories().subscribe({ next: (v) => this.courseCategories.set(v) });
    }
  }

  /** CSS conic-gradient stops for a donut chart from a list of {value, color} segments. */
  donutGradient(segments: ChartSegment[]): string {
    const total = segments.reduce((sum, s) => sum + s.value, 0);
    if (total === 0) return 'conic-gradient(var(--surface2) 0 100%)';
    let cursor = 0;
    const stops = segments.map((s) => {
      const start = (cursor / total) * 100;
      cursor += s.value;
      const end = (cursor / total) * 100;
      return `${s.color} ${start}% ${end}%`;
    });
    return `conic-gradient(${stops.join(', ')})`;
  }

  barWidth(value: number): string {
    return `${Math.round((value / this.maxBarValue()) * 100)}%`;
  }
}
