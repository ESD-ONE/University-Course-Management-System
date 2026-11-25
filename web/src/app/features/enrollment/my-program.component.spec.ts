import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MyProgramComponent } from './my-program.component';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { GradingService } from '../../core/services/grading.service';
import { ProgramCourseResponse } from '../../core/models/academic-structure.model';
import { CourseEnrollmentResponse, ProgramEnrollmentResponse } from '../../core/models/enrollment.model';
import { GradeHistoryResponse } from '../../core/models/grading.model';

describe('MyProgramComponent', () => {
  let enrollment: jasmine.SpyObj<EnrollmentService>;
  let academic: jasmine.SpyObj<AcademicStructureService>;
  let grading: jasmine.SpyObj<GradingService>;

  const activeProgram = { status: 'ACTIVE', degreeProgramId: 'p1', totalCredits: 120 } as ProgramEnrollmentResponse;
  const curriculum = [
    { catalogCourseCode: 'CS101', creditValue: 15 },
    { catalogCourseCode: 'CS102', creditValue: 15 },
    { catalogCourseCode: 'CS103', creditValue: 15 },
  ] as ProgramCourseResponse[];
  const grades = {
    courses: [{ courseCode: 'CS101', academicYearStatus: 'ARCHIVED', points: 4 }],
    gpa: 4,
    creditsGraded: 15,
  } as unknown as GradeHistoryResponse;

  function make(): MyProgramComponent {
    return TestBed.createComponent(MyProgramComponent).componentInstance;
  }

  beforeEach(() => {
    enrollment = jasmine.createSpyObj<EnrollmentService>('EnrollmentService', ['myProgramEnrollments', 'myEnrollments']);
    academic = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listCurriculum']);
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['myGrades']);

    enrollment.myProgramEnrollments.and.returnValue(of([activeProgram]));
    academic.listCurriculum.and.returnValue(of(curriculum));
    enrollment.myEnrollments.and.returnValue(of([{ courseCode: 'CS102', status: 'ENROLLED' } as CourseEnrollmentResponse]));
    grading.myGrades.and.returnValue(of(grades));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: EnrollmentService, useValue: enrollment },
        { provide: AcademicStructureService, useValue: academic },
        { provide: GradingService, useValue: grading },
      ],
    });
  });

  it('loads the active program and its curriculum', () => {
    const c = make();
    expect(c.program()).toBe(activeProgram);
    expect(c.curriculum().length).toBe(3);
    expect(c.loading()).toBeFalse();
  });

  it('classifies each course as completed / in-progress / not-started', () => {
    const c = make();
    const byCode = Object.fromEntries(c.rows().map((r) => [r.catalogCourseCode, r.progress]));
    expect(byCode['CS101']).toBe('completed'); // archived passing grade
    expect(byCode['CS102']).toBe('in-progress'); // currently enrolled
    expect(byCode['CS103']).toBe('not-started');
  });

  it('sums earned credits and the completion ratio', () => {
    const c = make();
    expect(c.creditsEarned()).toBe(15);
    expect(c.inProgress()).toBe(1);
    expect(c.creditRatio()).toBeCloseTo(0.125, 5);
  });

  it('ring() renders a conic-gradient', () => {
    const c = make();
    expect(c.ring()).toContain('conic-gradient');
  });

  it('handles a student with no active program', () => {
    enrollment.myProgramEnrollments.and.returnValue(of([{ status: 'WITHDRAWN' } as ProgramEnrollmentResponse]));
    const c = make();
    expect(c.program()).toBeNull();
    expect(c.curriculum()).toEqual([]);
    expect(c.loading()).toBeFalse();
    expect(academic.listCurriculum).not.toHaveBeenCalled();
  });

  it('records a load error', () => {
    enrollment.myProgramEnrollments.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
    expect(c.loading()).toBeFalse();
  });
});
