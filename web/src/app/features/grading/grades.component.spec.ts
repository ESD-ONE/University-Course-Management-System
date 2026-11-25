import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { GradesComponent } from './grades.component';
import { GradingService } from '../../core/services/grading.service';
import { GradeHistoryResponse } from '../../core/models/grading.model';

describe('GradesComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;

  const history = {
    gpa: 3.5,
    creditsGraded: 30,
    courses: [
      { courseCode: 'CS101', semesterName: 'Semester 1', academicYearName: '2024/2025' },
      { courseCode: 'CS102', semesterName: 'Semester 1', academicYearName: '2024/2025' },
      { courseCode: 'CS201', semesterName: 'Semester 2', academicYearName: '2024/2025' },
    ],
  } as unknown as GradeHistoryResponse;

  function make(): GradesComponent {
    return TestBed.createComponent(GradesComponent).componentInstance;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['myGrades']);
    grading.myGrades.and.returnValue(of(history));
    TestBed.configureTestingModule({ providers: [{ provide: GradingService, useValue: grading }] });
  });

  it('loads grade history on construction', () => {
    const c = make();
    expect(c.history()).toBe(history);
    expect(c.loading()).toBeFalse();
  });

  it('groups courses by semester + academic year', () => {
    const c = make();
    const groups = c.bySemester();
    expect(groups.length).toBe(2);
    expect(groups[0].label).toBe('Semester 1 · 2024/2025');
    expect(groups[0].rows.length).toBe(2);
    expect(groups[1].rows.length).toBe(1);
  });

  it('records a load error', () => {
    grading.myGrades.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
    expect(c.loading()).toBeFalse();
  });
});
