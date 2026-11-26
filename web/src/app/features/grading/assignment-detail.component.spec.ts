import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AssignmentDetailComponent } from './assignment-detail.component';
import { GradingService } from '../../core/services/grading.service';
import { AssignmentResponse, MySubmissionResponse } from '../../core/models/grading.model';

const DAY = 86400000;

describe('AssignmentDetailComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;

  function assignment(overrides: Partial<AssignmentResponse> = {}): AssignmentResponse {
    return {
      id: 'as-1',
      title: 'Essay 1',
      status: 'OPEN',
      dueDate: new Date(Date.now() + 3 * DAY).toISOString(),
      mySubmissionStatus: 'NOT_SUBMITTED',
      ...overrides,
    } as AssignmentResponse;
  }

  function make(a: AssignmentResponse = assignment()): AssignmentDetailComponent {
    const c = TestBed.createComponent(AssignmentDetailComponent).componentInstance;
    c.assignment = a;
    return c;
  }

  /** A file-input change event carrying one file of the given size. */
  function pickEvent(file: Partial<File>): Event {
    return { target: { files: [file], value: 'C:\\fakepath\\file' } } as unknown as Event;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', [
      'submit',
      'downloadBrief',
      'mySubmission',
      'downloadSubmission',
    ]);
    TestBed.configureTestingModule({
      imports: [AssignmentDetailComponent],
      providers: [{ provide: GradingService, useValue: grading }],
    });
  });

  it('allows submitting an open assignment that is not past due or graded', () => {
    expect(make().canSubmit()).toBeTrue();
  });

  it('blocks submitting when past due, closed or already graded', () => {
    expect(make(assignment({ dueDate: new Date(Date.now() - DAY).toISOString() })).canSubmit()).toBeFalse();
    expect(make(assignment({ status: 'CLOSED' })).canSubmit()).toBeFalse();
    expect(make(assignment({ mySubmissionStatus: 'GRADED' })).canSubmit()).toBeFalse();
  });

  it('labels the due date and the submission status', () => {
    const c = make();
    expect(c.dueLabel(assignment({ dueDate: new Date(Date.now() - DAY).toISOString() }))).toBe('Past due');
    expect(c.dueLabel(assignment({ dueDate: new Date(Date.now() + 1.5 * DAY).toISOString() }))).toBe('Due tomorrow');
    expect(c.dueLabel(assignment({ dueDate: new Date(Date.now() + 3.5 * DAY).toISOString() }))).toBe('Due in 3 days');
    expect(c.statusLabel(assignment())).toBe('Not submitted');
    expect(c.statusLabel(assignment({ mySubmissionStatus: 'SUBMITTED' }))).toBe('Submitted');
    expect(c.statusLabel(assignment({ mySubmissionStatus: 'GRADED' }))).toBe('Graded');
  });

  it('rejects a file larger than 20MB', () => {
    const c = make();
    c.pick(pickEvent({ name: 'big.zip', size: 20 * 1024 * 1024 + 1 }));
    expect(c.file()).toBeNull();
    expect(c.message()).toEqual({ text: 'That file is larger than 20MB.', error: true });
  });

  it('keeps a file of exactly 20MB', () => {
    const c = make();
    const file = { name: 'ok.zip', size: 20 * 1024 * 1024 };
    c.pick(pickEvent(file));
    expect(c.file()).toEqual(file as File);
    expect(c.message()).toBeNull();
  });

  it('submits the chosen file, confirms it and tells the parent', () => {
    grading.submit.and.returnValue(of({} as MySubmissionResponse));
    const c = make();
    const file = new File(['content'], 'essay.pdf');
    let submitted = 0;
    c.submitted.subscribe(() => submitted++);

    c.file.set(file);
    c.submit();

    expect(grading.submit).toHaveBeenCalledWith('as-1', file);
    expect(c.file()).toBeNull();
    expect(c.uploading()).toBeFalse();
    expect(c.message()).toEqual({ text: 'Submitted "essay.pdf".', error: false });
    expect(submitted).toBe(1);
  });

  it('does nothing when no file is chosen', () => {
    make().submit();
    expect(grading.submit).not.toHaveBeenCalled();
  });

  it('shows an error and keeps the file when the upload fails', () => {
    grading.submit.and.returnValue(throwError(() => new Error('network')));
    const c = make();
    const file = new File(['content'], 'essay.pdf');
    let submitted = 0;
    c.submitted.subscribe(() => submitted++);

    c.file.set(file);
    c.submit();

    expect(c.message()?.error).toBeTrue();
    expect(c.file()).toBe(file);
    expect(c.uploading()).toBeFalse();
    expect(submitted).toBe(0);
  });

  it('keeps the confirmation when the same assignment is reloaded, and clears it for another', () => {
    const c = make();
    c.message.set({ text: 'Submitted "essay.pdf".', error: false });

    c.assignment = assignment({ mySubmissionStatus: 'SUBMITTED' });
    expect(c.message()).not.toBeNull();

    c.assignment = assignment({ id: 'as-2' });
    expect(c.message()).toBeNull();
  });
});
