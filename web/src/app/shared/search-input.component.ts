import { Component, EventEmitter, Input, Output, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * A free-text search box with a search icon and a clear (×) button that appears once
 * something is typed. Works with ngModel/formControlName, or with [value]/(valueChange).
 */
@Component({
  selector: 'app-search-input',
  standalone: true,
  template: `
    <div class="search-input" [class.has-value]="!!value() && !disabled()">
      <svg class="search-input-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
        <circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" />
      </svg>
      <input
        type="text"
        [id]="inputId"
        [placeholder]="placeholder"
        [value]="value()"
        [disabled]="disabled()"
        autocomplete="off"
        (input)="onInput($any($event.target).value)"
        (blur)="onTouched()"
        (keydown.escape)="value() && clear($event)"
      />
      @if (value() && !disabled()) {
        <button type="button" class="input-clear" aria-label="Clear search" title="Clear" (mousedown)="clear($event)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      }
    </div>
  `,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SearchInputComponent), multi: true }],
})
export class SearchInputComponent implements ControlValueAccessor {
  @Input() placeholder = 'Search…';
  @Input() inputId: string | null = null;
  @Input('value') set valueInput(v: string | null) {
    this.value.set(v ?? '');
  }
  @Output() valueChange = new EventEmitter<string>();

  readonly value = signal('');
  readonly disabled = signal(false);
  private onChange: (v: string) => void = () => {};
  onTouched: () => void = () => {};

  writeValue(v: string | null): void {
    this.value.set(v ?? '');
  }
  registerOnChange(fn: (v: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(d: boolean): void {
    this.disabled.set(d);
  }

  onInput(v: string): void {
    this.value.set(v);
    this.onChange(v);
    this.valueChange.emit(v);
  }

  clear(event: Event): void {
    event.preventDefault();
    this.onInput('');
  }
}
