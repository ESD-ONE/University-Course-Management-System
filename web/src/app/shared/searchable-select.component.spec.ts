import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchableSelectComponent, SearchableSelectOption } from './searchable-select.component';

describe('SearchableSelectComponent', () => {
  let fixture: ComponentFixture<SearchableSelectComponent>;
  let component: SearchableSelectComponent;

  const OPTIONS: SearchableSelectOption[] = [
    { value: 'd1', label: 'Computer Science', sublabel: 'cs@uni.edu' },
    { value: 'd2', label: 'Mathematics' },
    { value: 'd3', label: 'Civil Engineering' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SearchableSelectComponent] }).compileComponents();
    fixture = TestBed.createComponent(SearchableSelectComponent);
    component = fixture.componentInstance;
    component.options = OPTIONS;
    fixture.detectChanges();
  });

  it('writeValue selects the option and shows its label as the query', () => {
    component.writeValue('d2');
    expect(component.selectedOption()?.label).toBe('Mathematics');
    expect(component.query()).toBe('Mathematics');
  });

  it('writeValue("") / null means nothing selected', () => {
    component.writeValue('d1');
    component.writeValue('');
    expect(component.selectedOption()).toBeNull();
    expect(component.query()).toBe('');
  });

  it('filteredOptions returns everything until the user actually types', () => {
    component.onFocus(); // opens but typed=false
    expect(component.filteredOptions().length).toBe(3);
  });

  it('filteredOptions matches on label and sublabel once typing', () => {
    component.onInput('comp'); // matches "Computer Science"
    expect(component.filteredOptions().map((o) => o.value)).toEqual(['d1']);

    component.onInput('cs@'); // matches d1 via sublabel
    expect(component.filteredOptions().map((o) => o.value)).toEqual(['d1']);

    component.onInput('engineering');
    expect(component.filteredOptions().map((o) => o.value)).toEqual(['d3']);
  });

  it('onInput opens the list, flags typed, and emits queryChange', () => {
    const queries: string[] = [];
    component.queryChange.subscribe((q) => queries.push(q));
    component.onInput('ma');
    expect(component.open()).toBeTrue();
    expect(component.typed()).toBeTrue();
    expect(queries).toEqual(['ma']);
  });

  it('select commits the value, emits valueChange, sets the label, and closes', () => {
    const changes: string[] = [];
    const emits: string[] = [];
    component.registerOnChange((v) => changes.push(v));
    component.valueChange.subscribe((v) => emits.push(v));

    component.select(OPTIONS[1]);

    expect(changes).toEqual(['d2']);
    expect(emits).toEqual(['d2']);
    expect(component.query()).toBe('Mathematics');
    expect(component.open()).toBeFalse();
  });

  it('commit de-duplicates: re-selecting the same value does not re-emit', () => {
    const emits: string[] = [];
    component.valueChange.subscribe((v) => emits.push(v));
    component.select(OPTIONS[0]);
    component.select(OPTIONS[0]);
    expect(emits).toEqual(['d1']);
  });

  it('clear resets to empty, emits both value and query changes, and closes', () => {
    component.select(OPTIONS[0]);
    const values: string[] = [];
    const queries: string[] = [];
    component.valueChange.subscribe((v) => values.push(v));
    component.queryChange.subscribe((q) => queries.push(q));

    component.clear(new MouseEvent('mousedown'));

    expect(component.selectedOption()).toBeNull();
    expect(component.query()).toBe('');
    expect(values).toEqual(['']);
    expect(queries).toEqual(['']);
    expect(component.open()).toBeFalse();
  });

  it('showClear is true only when clearable, enabled, and something is selected', () => {
    expect(component.showClear()).toBeFalse(); // nothing selected yet
    component.writeValue('d1');
    expect(component.showClear()).toBeTrue();

    component.setDisabledState(true);
    expect(component.showClear()).toBeFalse();

    component.setDisabledState(false);
    component.clearable = false;
    expect(component.showClear()).toBeFalse();
  });

  it('Enter selects the highlighted option', () => {
    const emits: string[] = [];
    component.valueChange.subscribe((v) => emits.push(v));
    component.onFocus();
    component.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' })); // highlight index 1
    component.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(emits).toEqual(['d2']);
  });

  it('onFocus is a no-op while disabled', () => {
    component.setDisabledState(true);
    component.onFocus();
    expect(component.open()).toBeFalse();
  });

  it('setting options after a value resolves the label', () => {
    const fresh = TestBed.createComponent(SearchableSelectComponent).componentInstance;
    fresh.writeValue('d3'); // value arrives before options
    expect(fresh.query()).toBe('');
    fresh.options = OPTIONS; // options arrive later
    expect(fresh.query()).toBe('Civil Engineering');
  });
});
