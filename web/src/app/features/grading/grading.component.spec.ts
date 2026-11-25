import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { GradingComponent } from './grading.component';
import { GradingService } from '../../core/services/grading.service';
import { AssignmentAnalyticsResponse, AssignmentResponse, GradeResponse, SubmissionRow } from '../../core/models/grading.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('GradingComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;
  let router: jasmine.SpyObj<Router>;
  const route = { snapshot: { queryParamMap: convertToParamMap({}) } };

  const assignments = [
    { id: 'a1', title: 'HW', status: 'OPEN', maxMarks: 100, gradesHidden: false },
    { id: 'a2', title: 'Draft', status: 'DRAFT', maxMarks: 100, gradesHidden: false },
  ] as AssignmentResponse[];

  const submissions = [
    { submissionId: 's1', status: 'GRADED', marks: 80, feedback: 'ok', studentName: 'Ada', fileName: 'f1' },
    { submissionId: 's2', status: 'SUBMITTED', marks: null, feedback: null, studentName: 'Bob', fileName: 'f2' },
    { submissionId: null, status: 'NOT_SUBMITTED', marks: null, feedback: null, studentName: 'Cat', fileName: null },
  ] as unknown as SubmissionRow[];

  function listPage() {
    return of({ content: assignments } as unknown as PagedResponse<AssignmentResponse>);
  }
  function make(): GradingComponent {
    return TestBed.createComponent(GradingComponent).componentInstance;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', [
      'list', 'submissions', 'assignmentAnalytics', 'setGradesHidden', 'releaseGrades', 'gradeAll', 'downloadSubmission',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    route.snapshot.queryParamMap = convertToParamMap({});

    grading.list.and.returnValue(listPage());
    grading.submissions.and.returnValue(of(submissions));

    TestBed.configureTestingModule({
      providers: [
        { provide: GradingService, useValue: grading },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
  });

  it('loads only gradable (non-draft) assignments, selects the first, and loads its submissions', () => {
    const c = make();
    expect(c.assignments().map((a) => a.id)).toEqual(['a1']); // draft a2 excluded
    expect(c.assignmentId()).toBe('a1');
    expect(c.rows().length).toBe(3);
  });

  it('computes submitted / graded / average', () => {
    const c = make();
    expect(c.submitted()).toBe(2); // GRADED + SUBMITTED, not the non-submitter
    expect(c.graded()).toBe(1);
    expect(c.average()).toBe(80); // single mark 80 / 100
  });

  it('dirty tracks edited rows only', () => {
    const c = make();
    expect(c.dirty().length).toBe(0);
    c.setMarks(c.rows()[1], '45'); // s2
    expect(c.dirty().map((r) => r.submissionId)).toEqual(['s2']);
  });

  it('saveAll sends the edited grades', () => {
    grading.gradeAll.and.returnValue(of([{} as GradeResponse]));
    const c = make();
    c.setMarks(c.rows()[1], '45');
    c.saveAll();
    expect(grading.gradeAll).toHaveBeenCalledWith('a1', [{ submissionId: 's2', marks: 45, feedback: null }]);
    expect(c.toast()?.message).toContain('saved');
  });

  it('saveAll rejects marks outside 0..maxMarks', () => {
    const c = make();
    c.setMarks(c.rows()[1], '150'); // > maxMarks 100
    c.saveAll();
    expect(grading.gradeAll).not.toHaveBeenCalled();
    expect(c.toast()?.error).toBeTrue();
  });

  it('select switches assignment, updates the URL, and reloads', () => {
    const c = make();
    grading.submissions.calls.reset();
    c.select('a1');
    expect(router.navigate).toHaveBeenCalledWith([], { queryParams: { assignment: 'a1' }, replaceUrl: true });
    expect(grading.submissions).toHaveBeenCalledWith('a1');
  });

  it('toggleAnalytics lazily loads analytics', () => {
    grading.assignmentAnalytics.and.returnValue(of({} as AssignmentAnalyticsResponse));
    const c = make();
    c.toggleAnalytics();
    expect(c.showAnalytics()).toBeTrue();
    expect(grading.assignmentAnalytics).toHaveBeenCalledWith('a1');
  });

  it('toggleGradesHidden hides when currently visible', () => {
    grading.setGradesHidden.and.returnValue(of(assignments[0]));
    const c = make();
    c.toggleGradesHidden();
    expect(grading.setGradesHidden).toHaveBeenCalledWith('a1', true);
    expect(grading.releaseGrades).not.toHaveBeenCalled();
  });

  it('toggleGradesHidden releases when currently hidden', () => {
    grading.list.and.returnValue(of({ content: [{ id: 'a1', status: 'OPEN', maxMarks: 100, gradesHidden: true }] } as unknown as PagedResponse<AssignmentResponse>));
    grading.releaseGrades.and.returnValue(of(void 0));
    const c = make();
    c.toggleGradesHidden();
    expect(grading.releaseGrades).toHaveBeenCalledWith('a1');
  });
});
