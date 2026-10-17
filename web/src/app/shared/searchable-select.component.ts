import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, HostListener, Input, Output, computed, forwardRef, inject, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SearchableSelectOption {
  value: string;
  label: string;
  /** Shown smaller, under the label, in the dropdown list only (e.g. an email). */
  sublabel?: string;
}

/**
 * A typing dropdown: looks and behaves like a text input, but only accepts one of the
 * given options (filtered as you type) — used everywhere a plain &lt;select&gt; would
 * otherwise force scrolling through a long list (Degree Program, Lecturer, Department,
 * Catalog Course). Drops into formControlName/ngModel exactly like a native input via
 * ControlValueAccessor; the bound value is the option's `value`, not its label.
 */
@Component({
  selector: 'app-searchable-select',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './searchable-select.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchableSelectComponent),
      multi: true,
    },
  ],
})
export class SearchableSelectComponent implements ControlValueAccessor {
  private host = inject(ElementRef<HTMLElement>);

  @Input() options: SearchableSelectOption[] = [];
  @Input() placeholder = 'Search…';
  @Input() inputId: string | null = null;
  @Output() valueChange = new EventEmitter<string>();

  readonly query = signal('');
  readonly open = signal(false);
  readonly highlightedIndex = signal(0);
  readonly disabled = signal(false);
  private selectedValue: string | null = null;

  private onChange: (value: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  readonly filteredOptions = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!this.open()) return this.options;
    if (!q) return this.options;
    return this.options.filter(
      (o) => o.label.toLowerCase().includes(q) || (o.sublabel?.toLowerCase().includes(q) ?? false),
    );
  });

  readonly selectedOption = computed(() => this.options.find((o) => o.value === this.selectedValue) ?? null);

  writeValue(value: string | null): void {
    this.selectedValue = value;
    const match = this.options.find((o) => o.value === value);
    this.query.set(match ? match.label : '');
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  onFocus(): void {
    if (this.disabled()) return;
    this.open.set(true);
    this.highlightedIndex.set(Math.max(0, this.filteredOptions().findIndex((o) => o.value === this.selectedValue)));
  }

  onInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
    this.highlightedIndex.set(0);
  }

  select(option: SearchableSelectOption): void {
    this.selectedValue = option.value;
    this.query.set(option.label);
    this.open.set(false);
    this.onChange(option.value);
    this.valueChange.emit(option.value);
  }

  onKeydown(event: KeyboardEvent): void {
    const options = this.filteredOptions();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.open.set(true);
      this.highlightedIndex.update((i) => Math.min(i + 1, options.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.highlightedIndex.update((i) => Math.max(i - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const opt = options[this.highlightedIndex()];
      if (opt) this.select(opt);
    } else if (event.key === 'Escape') {
      this.close();
    }
  }

  /** mousedown (not click) fires before the input's blur, so the selection registers
   * before close() would otherwise discard it. */
  onOptionMouseDown(event: Event, option: SearchableSelectOption): void {
    event.preventDefault();
    this.select(option);
  }

  close(): void {
    this.open.set(false);
    this.onTouched();
    // Revert stray typed text that doesn't match a real selection.
    const current = this.selectedOption();
    this.query.set(current ? current.label : '');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }
}
