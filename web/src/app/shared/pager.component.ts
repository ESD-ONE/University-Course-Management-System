import { CommonModule } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';

/**
 * Pager for a server-paginated list — "Showing X–Y of Z", a page-size selector, and
 * First/Prev/page-numbers/Next/Last. Extracted from users-list.component, the first
 * screen to do real API pagination; every other paginated list screen should use this
 * instead of hand-rolling it again.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (totalElements() > 0) {
      <div class="pagination-bar">
        <div class="pager-left">
          <span class="cell-faint">
            Showing {{ rangeStart() }}–{{ rangeEnd() }} of {{ totalElements() }} {{ itemLabel() }}
          </span>
          @if (showPageSize()) {
            <label class="pager-size">
              <span>Rows per page</span>
              <select [value]="pageSize()" (change)="pageSizeChange.emit(+$any($event.target).value)">
                @for (n of pageSizeOptions(); track n) { <option [value]="n">{{ n }}</option> }
              </select>
            </label>
          }
        </div>
        <div class="pager-nav">
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() === 0" (click)="go(0)" title="First page">«</button>
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() === 0" (click)="go(page() - 1)" title="Previous page">‹</button>
          @for (p of pageWindow(); track p) {
            <button type="button" class="pager-page" [class.active]="p === page()" (click)="go(p)">{{ p + 1 }}</button>
          }
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="go(page() + 1)" title="Next page">›</button>
          <button type="button" class="btn btn-secondary btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="go(totalPages() - 1)" title="Last page">»</button>
        </div>
      </div>
    }
  `,
})
export class PagerComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly itemLabel = input('items');
  /** Set to false for a list whose size is fixed by the caller rather than user-chosen. */
  readonly showPageSize = input(true);
  readonly pageSizeOptions = input<number[]>([10, 25, 50, 100]);

  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  readonly rangeStart = computed(() => this.page() * this.pageSize() + 1);
  readonly rangeEnd = computed(() => Math.min((this.page() + 1) * this.pageSize(), this.totalElements()));

  /** Up to 5 page numbers centered on the current page, clamped to the valid range. */
  readonly pageWindow = computed(() => {
    const total = this.totalPages();
    const current = this.page();
    const span = 2;
    let start = Math.max(0, current - span);
    const end = Math.min(total - 1, start + span * 2);
    start = Math.max(0, Math.min(start, end - span * 2));
    const window: number[] = [];
    for (let p = start; p <= end; p++) window.push(p);
    return window;
  });

  go(p: number): void {
    if (p >= 0 && p < this.totalPages() && p !== this.page()) this.pageChange.emit(p);
  }
}
