import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FilterBarComponent } from './filter-bar.component';

describe('FilterBarComponent', () => {
  let fixture: ComponentFixture<FilterBarComponent>;
  let component: FilterBarComponent;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
  function clearButton(): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.filter-bar-clear');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [FilterBarComponent] }).compileComponents();
    fixture = TestBed.createComponent(FilterBarComponent);
    component = fixture.componentInstance;
  });

  it('hides the active-count text and Clear button when no filters are active', () => {
    component.activeCount = 0;
    component.summary = 'Showing 12 of 12 courses';
    fixture.detectChanges();
    expect(clearButton()).toBeNull();
    expect(text()).toContain('Showing 12 of 12 courses');
    expect(text()).not.toContain('applied');
  });

  it('shows the Clear button and a singular "1 filter applied"', () => {
    component.activeCount = 1;
    fixture.detectChanges();
    expect(clearButton()).not.toBeNull();
    expect(text()).toContain('1 filter applied');
    expect(text()).not.toContain('1 filters');
  });

  it('pluralizes for more than one active filter', () => {
    component.activeCount = 3;
    fixture.detectChanges();
    expect(text()).toContain('3 filters applied');
  });

  it('emits clear when the Clear button is clicked', () => {
    component.activeCount = 2;
    fixture.detectChanges();
    let cleared = false;
    component.clear.subscribe(() => (cleared = true));
    clearButton()!.click();
    expect(cleared).toBeTrue();
  });

  it('sets the aria-label from the label input', () => {
    component.label = 'Course filters';
    fixture.detectChanges();
    const section = (fixture.nativeElement as HTMLElement).querySelector('.filter-bar')!;
    expect(section.getAttribute('aria-label')).toBe('Course filters');
  });
});
