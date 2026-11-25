import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LecturerCoursesComponent } from './lecturer-courses.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { CourseResponse, CourseAcademicYearResponse, AcademicYearResponse } from '../../core/models/academic-structure.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('LecturerCoursesComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;

  const course = { id: 'c1', catalogCourseCode: 'CS101', catalogCourseName: 'Intro', beginDate: '2025-09-01', yearOfStudy: 1, capacity: 40, status: 'PUBLISHED', degreeProgramId: 'p1', degreeProgramName: 'BSc', degreeProgramCode: 'BSC' } as CourseResponse;

  function coursesPage(items = [course]) {
    return of({ content: items, totalElements: items.length, totalPages: 1 } as unknown as PagedResponse<CourseResponse>);
  }
  function make(): LecturerCoursesComponent {
    return TestBed.createComponent(LecturerCoursesComponent).componentInstance;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', [
      'listAcademicYears', 'listCourses', 'updateCourse', 'deleteCourse',
      'listCourseAcademicYears', 'assignCourseToAcademicYear', 'updateCourseAcademicYear', 'deleteCourseAcademicYear',
    ]);
    svc.listAcademicYears.and.returnValue(of([{ id: 'y1', name: '2025/2026', semesters: [] }] as unknown as AcademicYearResponse[]));
    svc.listCourses.and.returnValue(coursesPage());

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AcademicStructureService, useValue: svc }],
    });
  });

  it('loads the lecturer’s own courses (mine=true) on construction', () => {
    const c = make();
    expect(svc.listCourses).toHaveBeenCalledWith(true, jasmine.anything());
    expect(c.courses().length).toBe(1);
    expect(c.allMyCourses().length).toBe(1);
  });

  it('builds the program filter options from all the lecturer’s courses', () => {
    const c = make();
    expect(c.programOptions().map((o) => o.value)).toEqual(['p1']);
  });

  it('openEditCourse seeds and submitEditCourse updates + reloads', () => {
    svc.updateCourse.and.returnValue(of(course));
    const c = make();
    c.openEditCourse(course);
    expect(c.editingCourse()).toBe(course);
    svc.listCourses.calls.reset();
    c.submitEditCourse();
    expect(svc.updateCourse).toHaveBeenCalledWith('c1', jasmine.anything());
    expect(svc.listCourses).toHaveBeenCalled();
  });

  it('deleteCourse confirms then deletes', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    svc.deleteCourse.and.returnValue(of(void 0));
    const c = make();
    c.deleteCourse(course);
    expect(svc.deleteCourse).toHaveBeenCalledWith('c1');
  });

  it('openManageYears loads year assignments and deleteYearAssignment removes one', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    svc.listCourseAcademicYears.and.returnValue(of([{ id: 'a1', academicYearName: '2025/2026' } as CourseAcademicYearResponse]));
    svc.deleteCourseAcademicYear.and.returnValue(of(void 0));
    const c = make();
    c.openManageYears(course);
    expect(c.yearAssignments().length).toBe(1);
    c.deleteYearAssignment(c.yearAssignments()[0]);
    expect(svc.deleteCourseAcademicYear).toHaveBeenCalledWith('c1', 'a1');
    expect(c.yearAssignments().length).toBe(0);
  });
});
