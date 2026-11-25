import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReferenceDataComponent } from './reference-data.component';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import {
  CourseCategoryResponse, DepartmentResponse, FacultyResponse, GradingScaleResponse, ReferenceCounts,
} from '../../core/models/reference-data.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('ReferenceDataComponent', () => {
  let svc: jasmine.SpyObj<ReferenceDataService>;

  function page<T>(content: T[]) {
    return of({ content, number: 0, totalElements: content.length, totalPages: 1 } as unknown as PagedResponse<T>);
  }
  const counts = { total: 3, active: 2, inactive: 1 } as ReferenceCounts;

  function make(): ReferenceDataComponent {
    const c = TestBed.createComponent(ReferenceDataComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<ReferenceDataService>('ReferenceDataService', [
      'listFaculties', 'listDepartments', 'listGradingScales', 'listCourseCategories',
      'facultyCounts', 'departmentCounts', 'gradingScaleCounts', 'courseCategoryCounts',
    ]);
    svc.listFaculties.and.returnValue(page([{ id: 'f1', name: 'Engineering', code: 'ENG' }] as FacultyResponse[]));
    svc.listDepartments.and.returnValue(page([{ id: 'd1', name: 'CS' }] as DepartmentResponse[]));
    svc.listGradingScales.and.returnValue(page([{ id: 'g1', name: 'Standard' }] as GradingScaleResponse[]));
    svc.listCourseCategories.and.returnValue(page([{ id: 'c1', name: 'Core' }] as CourseCategoryResponse[]));
    svc.facultyCounts.and.returnValue(of(counts));
    svc.departmentCounts.and.returnValue(of(counts));
    svc.gradingScaleCounts.and.returnValue(of(counts));
    svc.courseCategoryCounts.and.returnValue(of(counts));

    TestBed.configureTestingModule({ providers: [{ provide: ReferenceDataService, useValue: svc }] });
  });

  it('loads the faculties tab, the faculty picker list, and counts on init', () => {
    const c = make();
    expect(c.activeTab()).toBe('faculties');
    expect(c.facultyList().items.length).toBe(1);
    expect(c.allFaculties().length).toBe(1);
    expect(c.facultyCounts()).toEqual(counts);
  });

  it('activeList and activeTabCounts track the active tab', () => {
    const c = make();
    expect(c.activeList().items.length).toBe(1);
    expect(c.activeTabCounts()).toEqual(counts);
  });

  it('selectTab switches tab, resets filters, and loads that tab', () => {
    const c = make();
    c.searchFilter.set('x');
    c.selectTab('departments');
    expect(c.activeTab()).toBe('departments');
    expect(c.searchFilter()).toBe('');
    expect(svc.listDepartments).toHaveBeenCalled();
    expect(c.departmentList().items.length).toBe(1);
  });

  it('loads grading and category tabs on selection', () => {
    const c = make();
    c.selectTab('grading');
    expect(svc.listGradingScales).toHaveBeenCalled();
    c.selectTab('categories');
    expect(svc.listCourseCategories).toHaveBeenCalled();
  });

  it('counts active filters, including the department-only faculty filter', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.statusFilter.set('ACTIVE');
    expect(c.activeFilterCount()).toBe(1);
    c.selectTab('departments');
    c.facultyFilter.set('f1');
    expect(c.activeFilterCount()).toBe(1); // status was reset by selectTab; faculty now counts
  });

  it('filterSummary reflects the active tab label and total', () => {
    const c = make();
    expect(c.filterSummary()).toContain('faculties');
    expect(c.filterSummary()).toContain('1');
  });
});
