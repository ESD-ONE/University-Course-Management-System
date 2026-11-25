import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PagerComponent } from './pager.component';

describe('PagerComponent', () => {
  let fixture: ComponentFixture<PagerComponent>;
  let component: PagerComponent;

  function setInputs(page: number, totalPages: number, totalElements: number, pageSize = 10): void {
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', totalPages);
    fixture.componentRef.setInput('totalElements', totalElements);
    fixture.componentRef.setInput('pageSize', pageSize);
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PagerComponent] }).compileComponents();
    fixture = TestBed.createComponent(PagerComponent);
    component = fixture.componentInstance;
  });

  it('renders nothing when there are no results', () => {
    setInputs(0, 0, 0);
    expect((fixture.nativeElement as HTMLElement).querySelector('.pagination-bar')).toBeNull();
  });

  it('shows the "Showing X–Y of Z" summary with the item label', () => {
    fixture.componentRef.setInput('itemLabel', 'courses');
    setInputs(0, 5, 42, 10);
    expect(text()).toContain('Showing 1–10 of 42 courses');
  });

  it('computes rangeStart/rangeEnd, clamping the end to the total', () => {
    setInputs(4, 5, 42, 10); // last page: items 41–42
    expect(component.rangeStart()).toBe(41);
    expect(component.rangeEnd()).toBe(42);
  });

  it('builds a 5-wide page window centered on the current page', () => {
    setInputs(5, 10, 100, 10);
    expect(component.pageWindow()).toEqual([3, 4, 5, 6, 7]);
  });

  it('clamps the window at the start', () => {
    setInputs(0, 10, 100, 10);
    expect(component.pageWindow()).toEqual([0, 1, 2, 3, 4]);
  });

  it('shrinks the window when there are fewer pages than the span', () => {
    setInputs(0, 3, 25, 10);
    expect(component.pageWindow()).toEqual([0, 1, 2]);
  });

  it('go() emits pageChange only for a valid, different page', () => {
    setInputs(2, 10, 100, 10);
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.go(3); // valid
    component.go(2); // same page — ignored
    component.go(-1); // out of range — ignored
    component.go(10); // out of range — ignored

    expect(emitted).toEqual([3]);
  });

  it('disables First/Prev on the first page and Next/Last on the last', () => {
    setInputs(0, 3, 25, 10);
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.pager-nav .btn'));
    const [first, prev, next, last] = buttons;
    expect(first.disabled).toBeTrue();
    expect(prev.disabled).toBeTrue();
    expect(next.disabled).toBeFalse();
    expect(last.disabled).toBeFalse();

    setInputs(2, 3, 25, 10);
    const b2 = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.pager-nav .btn'));
    expect(b2[0].disabled).toBeFalse();
    expect(b2[2].disabled).toBeTrue();
    expect(b2[3].disabled).toBeTrue();
  });

  it('clicking a page number emits that page', () => {
    setInputs(0, 5, 50, 10);
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));
    const pageButtons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.pager-page');
    pageButtons[2].click(); // page index 2
    expect(emitted).toEqual([2]);
  });
});
