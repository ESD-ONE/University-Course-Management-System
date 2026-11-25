import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminCoursesComponent } from './admin-courses.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { CourseResponse, CourseAcademicYearResponse, DegreeProgramResponse, AcademicYearResponse } from '../../core/models/academic-structure.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('AdminCoursesComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;

  const course = { id: 'c1', catalogCourseCode: 'CS101', catalogCourseName: 'Intro', beginDate: '2025-09-01', yearOfStudy: 1, capacity: 40, status: 'PUBLISHED' } as CourseResponse;

  function coursesPage(items = [course]) {
    return of({ content: items, totalElements: items.length, totalPages: 1 } as unknown as PagedResponse<CourseResponse>);
  }
  function make(): AdminCoursesComponent {
    return TestBed.createComponent(AdminCoursesComponent).componentInstance;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', [
      'listDegreePrograms', 'listAcademicYears', 'listCourses', 'updateCourse', 'deleteCourse',
      'listCourseAcademicYears', 'assignCourseToAcademicYear', 'updateCourseAcademicYear', 'deleteCourseAcademicYear',
    ]);
    svc.listDegreePrograms.and.returnValue(of({ content: [{ id: 'p1', code: 'BSC', name: 'BSc' }] } as unknown as PagedResponse<DegreeProgramResponse>));
    svc.listAcademicYears.and.returnValue(of([{ id: 'y1', name: '2025/2026', semesters: [] }] as unknown as AcademicYearResponse[]));
    svc.listCourses.and.returnValue(coursesPage());

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AcademicStructureService, useValue: svc }],
    });
  });

  it('loads courses (admin scope: mine=false) and reference data on construction', () => {
    const c = make();
    expect(svc.listCourses).toHaveBeenCalledWith(false, jasmine.anything());
    expect(c.courses().length).toBe(1);
    expect(c.totalElements()).toBe(1);
    expect(c.degreePrograms().length).toBe(1);
  });

  it('counts active filters', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.onDegreeProgramFilterChange('p1');
    c.onStatusFilterChange('PUBLISHED');
    expect(c.activeFilterCount()).toBe(2);
  });

  it('debounces the search before reloading', fakeAsync(() => {
    const c = make();
    svc.listCourses.calls.reset();
    c.onSearchInput('cs');
    expect(svc.listCourses).not.toHaveBeenCalled();
    tick(300);
    expect(svc.listCourses).toHaveBeenCalled();
  }));

  it('openEditCourse seeds the edit form', () => {
    const c = make();
    c.openEditCourse(course);
    expect(c.editingCourse()).toBe(course);
    expect(c.editCourseForm.controls.capacity.value).toBe(40);
  });

  it('submitEditCourse updates and reloads', () => {
    svc.updateCourse.and.returnValue(of({ ...course, catalogCourseCode: 'CS101' }));
    const c = make();
    c.openEditCourse(course);
    svc.listCourses.calls.reset();
    c.submitEditCourse();
    expect(svc.updateCourse).toHaveBeenCalledWith('c1', jasmine.anything());
    expect(c.editingCourse()).toBeNull();
    expect(svc.listCourses).toHaveBeenCalled();
  });

  it('deleteCourse confirms then deletes', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    svc.deleteCourse.and.returnValue(of(void 0));
    const c = make();
    c.deleteCourse(course);
    expect(svc.deleteCourse).toHaveBeenCalledWith('c1');
  });

  it('openManageYears loads the course’s year assignments', () => {
    svc.listCourseAcademicYears.and.returnValue(of([{ id: 'a1', academicYearName: '2025/2026' } as CourseAcademicYearResponse]));
    const c = make();
    c.openManageYears(course);
    expect(c.managingCourse()).toBe(course);
    expect(c.yearAssignments().length).toBe(1);
  });

  it('submitYearAssignment assigns the course to a year', () => {
    svc.listCourseAcademicYears.and.returnValue(of([]));
    svc.assignCourseToAcademicYear.and.returnValue(of({ id: 'a2', academicYearName: '2025/2026' } as CourseAcademicYearResponse));
    const c = make();
    c.openManageYears(course);
    c.submitYearAssignment();
    expect(svc.assignCourseToAcademicYear).toHaveBeenCalledWith('c1', jasmine.anything());
    expect(c.yearAssignments().some((a) => a.id === 'a2')).toBeTrue();
  });
});
