import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MyAssignmentsComponent } from './my-assignments.component';
import { GradingService } from '../../core/services/grading.service';
import { AssignmentResponse } from '../../core/models/grading.model';
import { PagedResponse } from '../../core/models/pagination.model';

// Submitting, the due label and the brief live in AssignmentDetailComponent (see its own
// spec); this screen only loads the list and decides which assignment is selected.
describe('MyAssignmentsComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;

  const DAY = 86400000;
  const inDays = (n: number) => new Date(Date.now() + n * DAY).toISOString();

  function assignment(p: Partial<AssignmentResponse>): AssignmentResponse {
    return { id: 'a1', title: 'HW1', status: 'OPEN', dueDate: inDays(2), mySubmissionStatus: 'NOT_SUBMITTED', maxMarks: 100, ...p } as AssignmentResponse;
  }
  function list(items: AssignmentResponse[]) {
    return of({ content: items } as unknown as PagedResponse<AssignmentResponse>);
  }
  function make(): MyAssignmentsComponent {
    return TestBed.createComponent(MyAssignmentsComponent).componentInstance;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['list']);
    grading.list.and.returnValue(list([assignment({ id: 'a1' })]));
    TestBed.configureTestingModule({ providers: [{ provide: GradingService, useValue: grading }] });
  });

  it('loads assignments and auto-selects an open, unsubmitted one', () => {
    grading.list.and.returnValue(list([
      assignment({ id: 'done', status: 'OPEN', mySubmissionStatus: 'SUBMITTED' }),
      assignment({ id: 'todo', status: 'OPEN', mySubmissionStatus: 'NOT_SUBMITTED' }),
    ]));
    const c = make();
    expect(c.selectedId()).toBe('todo');
    expect(c.selected()?.id).toBe('todo');
    expect(c.loading()).toBeFalse();
  });

  it('falls back to the first assignment when none is waiting for a submission', () => {
    grading.list.and.returnValue(list([
      assignment({ id: 'later', dueDate: inDays(5), mySubmissionStatus: 'GRADED' }),
      assignment({ id: 'sooner', dueDate: inDays(1), mySubmissionStatus: 'SUBMITTED' }),
    ]));
    const c = make();
    expect(c.selectedId()).toBe('sooner');
  });

  it('lists assignments by due date, soonest first', () => {
    grading.list.and.returnValue(list([
      assignment({ id: 'later', dueDate: inDays(5) }),
      assignment({ id: 'sooner', dueDate: inDays(1) }),
    ]));
    const c = make();
    expect(c.assignments().map((a) => a.id)).toEqual(['sooner', 'later']);
  });

  it('keeps the current selection when the list is reloaded', () => {
    const c = make();
    c.selectedId.set('a1');
    grading.list.and.returnValue(list([assignment({ id: 'a0', dueDate: inDays(1) }), assignment({ id: 'a1' })]));
    c.load();
    expect(c.selectedId()).toBe('a1');
  });

  it('shows an error when the list cannot be loaded', () => {
    grading.list.and.returnValue(throwError(() => new Error('network')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
    expect(c.loading()).toBeFalse();
  });

  it('statusLabel describes the student\'s own submission', () => {
    const c = make();
    expect(c.statusLabel(assignment({}))).toBe('To do');
    expect(c.statusLabel(assignment({ mySubmissionStatus: 'SUBMITTED' }))).toBe('Submitted');
    expect(c.statusLabel(assignment({ mySubmissionStatus: 'GRADED' }))).toBe('Graded');
  });
});
