import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LearningOfferingNode, LearningProgramNode, LearningYearNode } from '../../core/models/learning.model';
import { LearningService } from '../../core/services/learning.service';
import { errorMessage } from '../../shared/ui';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import { SearchInputComponent } from '../../shared/search-input.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, matchesText, paginate, statusOptions } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 12;

interface MergedOffering extends LearningOfferingNode {
  academicYearId: string;
  academicYearName: string;
}

/** Learning Hub, level 3: the courses this user is assigned to within one degree
 * program — reached either for one academic year (clicking a program card on
 * `/app/learning/years/:yearId`) or, with no yearId in the route, merged across every
 * year the program appears in (clicking its card on the Learning Hub's landing page).
 * Choosing a course opens its week-by-week workspace, same as before the redesign. */
@Component({
  selector: 'app-learning-program-courses',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SearchableSelectComponent, SearchInputComponent, FilterBarComponent, PagerComponent],
  templateUrl: './learning-program-courses.component.html',
})
export class LearningProgramCoursesComponent {
  private learning = inject(LearningService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly yearId = this.route.snapshot.paramMap.get('yearId');
  readonly programId = this.route.snapshot.paramMap.get('programId') ?? '';

  readonly year = signal<LearningYearNode | null>(null);
  readonly program = signal<LearningProgramNode | null>(null);
  /** Only populated when there's no yearId in the route — the program's code/name and
   * every offering across every year it appears in, each tagged with its own year. */
  readonly programMeta = signal<{ code: string; name: string } | null>(null);
  readonly mergedOfferings = signal<MergedOffering[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly q = signal('');
  readonly statusFilter = signal('');
  readonly yearFilter = signal('');
  readonly statusFilterOptions = statusOptions(['DRAFT', 'PUBLISHED', 'CANCELLED']);
  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly programCode = computed(() => (this.yearId ? this.program()?.degreeProgramCode : this.programMeta()?.code));
  readonly programName = computed(() => (this.yearId ? this.program()?.degreeProgramName : this.programMeta()?.name));

  readonly yearOptions = computed<SearchableSelectOption[]>(() => {
    const seen = new Map<string, string>();
    for (const o of this.mergedOfferings()) seen.set(o.academicYearId, o.academicYearName);
    return [...seen.entries()].map(([value, label]) => ({ value, label }));
  });

  constructor() {
    this.learning.tree().subscribe({
      next: (tree) => {
        if (this.yearId) {
          const y = tree.years.find((y) => y.academicYearId === this.yearId) ?? null;
          this.year.set(y);
          this.program.set(y?.programs.find((p) => p.degreeProgramId === this.programId) ?? null);
        } else {
          let meta: { code: string; name: string } | null = null;
          const merged: MergedOffering[] = [];
          for (const y of tree.years) {
            const p = y.programs.find((p) => p.degreeProgramId === this.programId);
            if (!p) continue;
            meta = { code: p.degreeProgramCode, name: p.degreeProgramName };
            for (const o of p.offerings) merged.push({ ...o, academicYearId: y.academicYearId, academicYearName: y.academicYearName });
          }
          this.programMeta.set(meta);
          this.mergedOfferings.set(merged);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load this program’s courses.'));
        this.loading.set(false);
      },
    });
  }

  readonly courses = computed<MergedOffering[]>(() =>
    this.yearId ? ((this.program()?.offerings ?? []) as MergedOffering[]) : this.mergedOfferings(),
  );
  readonly filteredCourses = computed(() =>
    this.courses().filter(
      (c) =>
        matchesText(this.q(), c.code, c.name) &&
        (!this.statusFilter() || c.status === this.statusFilter()) &&
        (!this.yearFilter() || c.academicYearId === this.yearFilter()),
    ),
  );
  readonly pagedCourses = computed(() => paginate(this.filteredCourses(), this.page(), this.pageSize()));
  readonly totalElements = computed(() => this.filteredCourses().length);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / this.pageSize())));

  onSearchInput(value: string): void {
    this.q.set(value);
    this.page.set(0);
  }

  onStatusFilterChange(value: string): void {
    this.statusFilter.set(value);
    this.page.set(0);
  }

  onYearFilterChange(value: string): void {
    this.yearFilter.set(value);
    this.page.set(0);
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  activeFilterCount(): number {
    return countActive(this.q().trim(), this.statusFilter(), this.yearFilter());
  }

  filterSummary(): string {
    if (this.loading() || this.loadError()) return '';
    return `${this.totalElements()} course${this.totalElements() === 1 ? '' : 's'}${this.activeFilterCount() ? ' match' : ' in total'}`;
  }

  clearFilters(): void {
    this.q.set('');
    this.statusFilter.set('');
    this.yearFilter.set('');
    this.page.set(0);
  }

  openOffering(offeringId: string): void {
    this.router.navigate(['/app/learning/offerings', offeringId]);
  }
}
