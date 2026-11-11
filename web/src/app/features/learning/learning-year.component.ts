import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LearningProgramNode, LearningYearNode } from '../../core/models/learning.model';
import { LearningService } from '../../core/services/learning.service';
import { errorMessage } from '../../shared/ui';
import { SearchInputComponent } from '../../shared/search-input.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, matchesText, paginate } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 12;

/** Learning Hub, level 2: the degree programs this user is assigned to within one
 * academic year — a dedicated page (not an expanded section) reached by clicking that
 * year's card on `/app/learning`. */
@Component({
  selector: 'app-learning-year',
  standalone: true,
  imports: [CommonModule, RouterLink, SearchInputComponent, FilterBarComponent, PagerComponent],
  templateUrl: './learning-year.component.html',
})
export class LearningYearComponent {
  private learning = inject(LearningService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly yearId = this.route.snapshot.paramMap.get('yearId') ?? '';
  readonly year = signal<LearningYearNode | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly q = signal('');
  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  constructor() {
    this.learning.tree().subscribe({
      next: (tree) => {
        this.year.set(tree.years.find((y) => y.academicYearId === this.yearId) ?? null);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load this academic year.'));
        this.loading.set(false);
      },
    });
  }

  readonly programs = computed<LearningProgramNode[]>(() => this.year()?.programs ?? []);
  readonly filteredPrograms = computed(() =>
    this.programs().filter((p) => matchesText(this.q(), p.degreeProgramCode, p.degreeProgramName)),
  );
  readonly pagedPrograms = computed(() => paginate(this.filteredPrograms(), this.page(), this.pageSize()));
  readonly totalElements = computed(() => this.filteredPrograms().length);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / this.pageSize())));

  onSearchInput(value: string): void {
    this.q.set(value);
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
    return countActive(this.q().trim());
  }

  filterSummary(): string {
    if (this.loading() || this.loadError()) return '';
    return `${this.totalElements()} degree program${this.totalElements() === 1 ? '' : 's'}${this.activeFilterCount() ? ' match' : ' in total'}`;
  }

  clearFilters(): void {
    this.q.set('');
    this.page.set(0);
  }

  openProgram(program: LearningProgramNode): void {
    this.router.navigate(['/app/learning/years', this.yearId, 'programs', program.degreeProgramId]);
  }
}
