import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * The one filter layout every list screen uses: the filter fields (projected, each a
 * `<div class="filter-field">` with a label and a typing dropdown or search box), a
 * result count, and a single "Clear filters" action once anything is filtered.
 */
@Component({
  selector: 'app-filter-bar',
  standalone: true,
  template: `
    <section class="filter-bar" [attr.aria-label]="label">
      <div class="filter-bar-fields"><ng-content /></div>
      <div class="filter-bar-foot">
        <span class="filter-bar-summary">
          @if (activeCount > 0) {
            <span class="filter-bar-active">{{ activeCount }} filter{{ activeCount === 1 ? '' : 's' }} applied</span>
            @if (summary) { <span class="filter-bar-dot">·</span> }
          }
          {{ summary }}
        </span>
        @if (activeCount > 0) {
          <button type="button" class="btn-link filter-bar-clear" (click)="clear.emit()">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
            Clear filters
          </button>
        }
      </div>
    </section>
  `,
})
export class FilterBarComponent {
  /** How many filters currently narrow the list (0 hides "Clear filters"). */
  @Input() activeCount = 0;
  /** e.g. "Showing 3 of 12 courses". */
  @Input() summary = '';
  @Input() label = 'Filters';
  @Output() clear = new EventEmitter<void>();
}
