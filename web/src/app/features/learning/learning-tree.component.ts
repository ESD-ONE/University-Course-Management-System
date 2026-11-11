import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LearningProgramNode, LearningYearNode } from '../../core/models/learning.model';
import { LearningService } from '../../core/services/learning.service';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../shared/ui';
import { SearchInputComponent } from '../../shared/search-input.component';
import { FilterBarComponent } from '../../shared/filter-bar.component';
import { PagerComponent } from '../../shared/pager.component';
import { countActive, matchesText, paginate } from '../../shared/filters';

const DEFAULT_PAGE_SIZE = 12;

/**
 * "Academic Years" — the Learning Hub's entry point, placed right below Dashboard in
 * the nav. Scoped to the signed-in user: a student sees only years/programs they're
 * actually enrolled in, a lecturer only years/programs they teach in or are scoped to
 * (an admin sees everything — see LearningService.tree(), unchanged by this redesign).
 * Never a program with nothing actually assigned to this person — see
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 4.3.
 */
@Component({
  selector: 'app-learning-tree',
  standalone: true,
  imports: [CommonModule, SearchInputComponent, FilterBarComponent, PagerComponent],
  templateUrl: './learning-tree.component.html',
})
export class LearningTreeComponent {
  private learning = inject(LearningService);
  private router = inject(Router);
  private auth = inject(AuthService);
  readonly isAdmin = this.auth.currentUser()?.role === 'ADMIN';

  readonly years = signal<LearningYearNode[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly q = signal('');
  readonly page = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  constructor() {
    this.learning.tree().subscribe({
      next: (tree) => {
        this.years.set(tree.years);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load your academic years.'));
        this.loading.set(false);
      },
    });
  }

  /** Every degree program assigned to this user, deduplicated across the years they
   * appear in (the same program can span several years of study). */
  readonly allPrograms = computed(() => {
    const seen = new Map<string, LearningProgramNode>();
    for (const y of this.years()) {
      for (const p of y.programs) {
        if (!seen.has(p.degreeProgramId)) seen.set(p.degreeProgramId, p);
      }
    }
    return [...seen.values()].sort((a, b) => a.degreeProgramCode.localeCompare(b.degreeProgramCode));
  });

  readonly filteredPrograms = computed(() =>
    this.allPrograms().filter((p) => matchesText(this.q(), p.degreeProgramCode, p.degreeProgramName)),
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

  openYear(yearId: string): void {
    this.router.navigate(['/app/learning/years', yearId]);
  }

  openProgram(programId: string): void {
    this.router.navigate(['/app/learning/programs', programId]);
  }
}
