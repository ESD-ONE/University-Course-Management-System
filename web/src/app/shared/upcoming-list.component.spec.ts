import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UpcomingListComponent } from './upcoming-list.component';
import { AuthService } from '../core/services/auth.service';
import { UpcomingItem } from '../core/models/grading.model';

const HOUR = 3600000;

describe('UpcomingListComponent', () => {
  let fixture: ComponentFixture<UpcomingListComponent>;
  let role: string;

  function item(type: 'LECTURE' | 'ASSIGNMENT', offsetMs: number): UpcomingItem {
    return {
      type,
      title: type === 'LECTURE' ? 'Week 3 lecture' : 'Essay 1',
      courseCode: 'CS101',
      courseName: 'Algorithms',
      offeringId: 'off-1',
      assignmentId: 'as-1',
      at: new Date(Date.now() + offsetMs).toISOString(),
    } as UpcomingItem;
  }

  function render(items: UpcomingItem[]): HTMLElement {
    fixture = TestBed.createComponent(UpcomingListComponent);
    fixture.componentRef.setInput('items', items);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    role = 'STUDENT';
    TestBed.configureTestingModule({
      imports: [UpcomingListComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: () => ({ role }) } },
      ],
    });
  });

  it('shows the empty message with the look-ahead window when there is nothing', () => {
    const el = render([]);
    expect(el.querySelector('.upcoming-list')).toBeNull();
    expect(el.textContent).toContain('Nothing coming up in the next 7 days.');
  });

  it('describes how far away each item is', () => {
    render([item('LECTURE', 0.5 * HOUR), item('LECTURE', 2.1 * HOUR), item('ASSIGNMENT', 72.1 * HOUR)]);
    expect(fixture.componentInstance.rows().map((r) => r.relative)).toEqual([
      'In less than an hour',
      'In 2h',
      'In 3 days',
    ]);
  });

  it('marks an item whose time has passed as overdue', () => {
    const el = render([item('ASSIGNMENT', -HOUR)]);
    expect(el.querySelector('.danger-text')?.textContent).toContain('Overdue');
  });

  it('links a lecture to its course workspace', () => {
    const el = render([item('LECTURE', 2 * HOUR)]);
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/app/learning/offerings/off-1');
  });

  it('sends a student to their assignments', () => {
    const el = render([item('ASSIGNMENT', 2 * HOUR)]);
    const link = el.querySelector('a');
    expect(link?.textContent).toContain('Open');
    expect(link?.getAttribute('href')).toBe('/app/my-assignments');
  });

  it('sends a lecturer to grading for that assignment', () => {
    role = 'LECTURER';
    const el = render([item('ASSIGNMENT', 2 * HOUR)]);
    const link = el.querySelector('a');
    expect(link?.textContent).toContain('Grade');
    expect(link?.getAttribute('href')).toBe('/app/grading?assignment=as-1');
  });
});
