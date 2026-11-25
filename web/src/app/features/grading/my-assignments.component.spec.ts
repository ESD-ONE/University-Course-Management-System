import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MyAssignmentsComponent } from './my-assignments.component';
import { GradingService } from '../../core/services/grading.service';
import { AssignmentResponse, MySubmissionResponse } from '../../core/models/grading.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('MyAssignmentsComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;

  const future = new Date(Date.now() + 2 * 86400000).toISOString();
  const past = new Date(Date.now() - 86400000).toISOString();

  function assignment(p: Partial<AssignmentResponse>): AssignmentResponse {
    return { id: 'a1', title: 'HW1', status: 'OPEN', dueDate: future, mySubmissionStatus: 'NOT_SUBMITTED', maxMarks: 100, ...p } as AssignmentResponse;
  }
  function list(items: AssignmentResponse[]) {
    return of({ content: items } as unknown as PagedResponse<AssignmentResponse>);
  }
  function make(): MyAssignmentsComponent {
    return TestBed.createComponent(MyAssignmentsComponent).componentInstance;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['list', 'submit', 'downloadBrief']);
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
  });

  it('canSubmit is true for an open, not-past-due, ungraded assignment', () => {
    const c = make();
    expect(c.canSubmit()).toBeTrue();
  });

  it('canSubmit is false once past due', () => {
    grading.list.and.returnValue(list([assignment({ id: 'a1', dueDate: past })]));
    const c = make();
    expect(c.pastDue()).toBeTrue();
    expect(c.canSubmit()).toBeFalse();
  });

  it('canSubmit is false once graded', () => {
    grading.list.and.returnValue(list([assignment({ id: 'a1', mySubmissionStatus: 'GRADED' })]));
    const c = make();
    expect(c.canSubmit()).toBeFalse();
  });

  it('dueLabel describes the remaining time', () => {
    const c = make();
    expect(c.dueLabel(assignment({ dueDate: past }))).toBe('Past due');
    expect(c.dueLabel(assignment({ dueDate: new Date(Date.now() + 2 * 86400000).toISOString() }))).toContain('Due in');
  });

  it('pick rejects a file over 20MB', () => {
    const c = make();
    const big = new File([new Uint8Array(21 * 1024 * 1024)], 'big.pdf');
    c.pick({ target: { files: [big] } } as unknown as Event);
    expect(c.file()).toBeNull();
    expect(c.toast()?.error).toBeTrue();
  });

  it('submit uploads the file for the selected assignment and reloads', () => {
    grading.submit.and.returnValue(of({} as MySubmissionResponse));
    const c = make();
    const f = new File(['x'], 'hw.pdf');
    c.pick({ target: { files: [f] } } as unknown as Event);
    grading.list.calls.reset();
    c.submit();
    expect(grading.submit).toHaveBeenCalledWith('a1', f);
    expect(c.file()).toBeNull();
    expect(grading.list).toHaveBeenCalled();
  });

  it('submit does nothing with no file selected', () => {
    const c = make();
    c.submit();
    expect(grading.submit).not.toHaveBeenCalled();
  });

  it('downloadBrief saves the returned blob', () => {
    const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    grading.downloadBrief.and.returnValue(of(new Blob()));
    const c = make();
    c.downloadBrief(assignment({ id: 'a1', briefFileName: 'brief.pdf' }));
    expect(grading.downloadBrief).toHaveBeenCalledWith('a1');
    expect(createUrl).toHaveBeenCalled();
  });
});
