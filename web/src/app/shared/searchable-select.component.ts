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
 * given options (filtered as you type). Drops into formControlName/ngModel exactly like
 * a native input via ControlValueAccessor; the bound value is the option's `value`, not
 * its label, and '' means "nothing selected".
 *
 * As a filter, give it a placeholder such as "All Departments": empty shows that, a
 * click lists every option, and the clear button (shown only once something is picked)
 * goes back to "all".
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

  private readonly optionsSignal = signal<SearchableSelectOption[]>([]);
  @Input() set options(value: SearchableSelectOption[]) {
    this.optionsSignal.set(value ?? []);
    // Options often arrive after the value (async load) — show the label once they do.
    if (!this.open()) this.query.set(this.selectedOption()?.label ?? '');
  }
  get options(): SearchableSelectOption[] {
    return this.optionsSignal();
  }
  @Input() placeholder = 'Search…';
  @Input() inputId: string | null = null;
  /** Show the clear (×) button once something is selected. */
  @Input() clearable = true;
  /** Text shown in the list when nothing matches (e.g. while a remote search runs). */
  @Input() emptyText = 'No matches';
  @Output() valueChange = new EventEmitter<string>();
  /** What the user is typing — for screens that search on the server instead of
   * filtering a list they already have. */
  @Output() queryChange = new EventEmitter<string>();

  readonly query = signal('');
  readonly open = signal(false);
  /** False until the user types after opening: a fresh click lists every option rather
   * than only those matching the currently selected label. */
  readonly typed = signal(false);
  readonly highlightedIndex = signal(0);
  readonly disabled = signal(false);
  private readonly selectedValue = signal<string | null>(null);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  readonly filteredOptions = computed(() => {
    const options = this.optionsSignal();
    const q = this.query().trim().toLowerCase();
    if (!this.typed() || !q) return options;
    return options.filter(
      (o) => o.label.toLowerCase().includes(q) || (o.sublabel?.toLowerCase().includes(q) ?? false),
    );
  });

  readonly selectedOption = computed(
    () => this.optionsSignal().find((o) => o.value === this.selectedValue()) ?? null,
  );
  readonly showClear = computed(() => this.clearable && !this.disabled() && !!this.selectedValue());

  writeValue(value: string | null): void {
    this.selectedValue.set(value || null);
    this.query.set(this.selectedOption()?.label ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  onFocus(): void {
    if (this.disabled() || this.open()) return;
    this.typed.set(false);
    this.open.set(true);
    this.highlightedIndex.set(Math.max(0, this.filteredOptions().findIndex((o) => o.value === this.selectedValue())));
  }

  onInput(value: string): void {
    this.query.set(value);
    this.typed.set(true);
    this.open.set(true);
    this.highlightedIndex.set(0);
    this.queryChange.emit(value);
  }

  select(option: SearchableSelectOption): void {
    this.commit(option.value);
    this.query.set(option.label);
    this.open.set(false);
  }

  clear(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.commit('');
    this.query.set('');
    this.open.set(false);
    this.queryChange.emit('');
    this.onTouched();
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
      if (!this.open()) return;
      event.preventDefault();
      const opt = options[this.highlightedIndex()];
      if (opt) this.select(opt);
    } else if (event.key === 'Escape' && this.open()) {
      event.stopPropagation();
      this.close();
    } else if (event.key === 'Tab') {
      this.close();
    }
  }

  /** mousedown (not click) fires before the input's blur, so the selection registers
   * before close() would otherwise discard it. */
  onOptionMouseDown(event: Event, option: SearchableSelectOption): void {
    event.preventDefault();
    this.select(option);
  }

  toggleFromCaret(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    if (this.disabled()) return;
    if (this.open()) {
      this.close();
    } else {
      input.focus();
      this.onFocus();
    }
  }

  close(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.typed.set(false);
    this.onTouched();
    // Revert stray typed text that doesn't match a real selection.
    this.query.set(this.selectedOption()?.label ?? '');
  }

  private commit(value: string): void {
    if ((this.selectedValue() ?? '') === value) return;
    this.selectedValue.set(value || null);
    this.onChange(value);
    this.valueChange.emit(value);
  }

  /** pointerdown, not click: modal boxes stop click propagation (so a click inside the
   * dialog doesn't close the dialog), which meant a click on the dialog's background
   * never reached a document click listener and the list stayed open. */
  @HostListener('document:pointerdown', ['$event'])
  onDocumentPointerDown(event: PointerEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }
}
