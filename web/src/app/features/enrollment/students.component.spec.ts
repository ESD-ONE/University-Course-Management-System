import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { StudentsComponent } from './students.component';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { CourseEnrollmentResponse, MyStudentResponse } from '../../core/models/enrollment.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('StudentsComponent', () => {
  let enrollment: jasmine.SpyObj<EnrollmentService>;
  let academic: jasmine.SpyObj<AcademicStructureService>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  const route = { snapshot: { queryParamMap: convertToParamMap({}) } };

  const offerings = [
    { id: 'o1', code: 'CS101', name: 'Intro', academicYearName: '2025/2026', academicYearStatus: 'ACTIVE', degreeProgramId: 'p1' },
    { id: 'o2', code: 'OLD', name: 'Archived', academicYearName: '2019/2020', academicYearStatus: 'ARCHIVED', degreeProgramId: 'p1' },
  ] as CourseOfferingResponse[];

  const students = [
    { name: 'Ada', studentNumber: 'S1', programName: 'BSc CS', yearOfStudy: 1 },
    { name: 'Bob', studentNumber: 'S2', programName: 'BSc Math', yearOfStudy: 2 },
  ] as MyStudentResponse[];

  const rosterRows = [
    { id: 'r1', status: 'ENROLLED', requirementType: 'CORE', studentName: 'Ada', studentNumber: 'S1', studentEmail: 'ada@x', courseCode: 'CS101', studentProgramName: 'BSc CS', studentYearOfStudy: 1, enrolledOn: '2025-01-01' },
    { id: 'r2', status: 'WAITLISTED', requirementType: 'ELECTIVE', studentName: 'Bob', studentNumber: 'S2', studentEmail: 'bob@x', courseCode: 'CS101', studentProgramName: 'BSc Math', studentYearOfStudy: 2, enrolledOn: '2025-01-02' },
  ] as CourseEnrollmentResponse[];

  function rosterPage() {
    return of({ content: rosterRows, totalElements: 2, totalPages: 1 } as unknown as PagedResponse<CourseEnrollmentResponse>);
  }
  function make(): StudentsComponent {
    return TestBed.createComponent(StudentsComponent).componentInstance;
  }

  beforeEach(() => {
    enrollment = jasmine.createSpyObj<EnrollmentService>('EnrollmentService', ['roster', 'myStudents', 'drop']);
    academic = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listCourseOfferings']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    route.snapshot.queryParamMap = convertToParamMap({});

    auth.currentUser.and.returnValue({ role: 'LECTURER' } as ProfileResponse);
    academic.listCourseOfferings.and.returnValue(of(offerings));
    enrollment.myStudents.and.returnValue(of(students));
    enrollment.roster.and.returnValue(rosterPage());

    TestBed.configureTestingModule({
      providers: [
        { provide: EnrollmentService, useValue: enrollment },
        { provide: AcademicStructureService, useValue: academic },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
  });

  it('loads non-archived offerings (mine=true for a lecturer) and the cross-course student list', () => {
    const c = make();
    expect(academic.listCourseOfferings).toHaveBeenCalledWith({ mine: true });
    expect(c.offerings().map((o) => o.id)).toEqual(['o1']); // archived o2 dropped
    expect(c.allStudents().length).toBe(2);
    expect(c.loading()).toBeFalse();
  });

  it('derives program options from the cross-course list when no offering is chosen', () => {
    const c = make();
    expect(c.programs().sort()).toEqual(['BSc CS', 'BSc Math']);
  });

  it('filteredAll narrows by program, year and search', () => {
    const c = make();
    c.onProgramFilterChange('BSc CS');
    expect(c.filteredAll().map((s) => s.name)).toEqual(['Ada']);
    c.onProgramFilterChange('');
    c.onSearchChange('bob');
    expect(c.filteredAll().map((s) => s.name)).toEqual(['Bob']);
  });

  it('selectOffering switches to the roster view, resets filters and updates the URL', () => {
    const c = make();
    c.onProgramFilterChange('BSc CS');
    c.selectOffering('o1');
    expect(c.offeringId()).toBe('o1');
    expect(c.programFilter()).toBe('');
    expect(router.navigate).toHaveBeenCalledWith([], { queryParams: { offering: 'o1' }, replaceUrl: true });
    expect(enrollment.roster).toHaveBeenCalled();
    expect(c.roster().length).toBe(2);
  });

  it('computes roster stat tiles from the full roster', () => {
    const c = make();
    c.selectOffering('o1');
    expect(c.enrolledCount()).toBe(1);
    expect(c.waitlistCount()).toBe(1);
    expect(c.coreCount()).toBe(1);
  });

  it('remove confirms, drops, and reloads', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    enrollment.drop.and.returnValue(of(void 0));
    const c = make();
    c.selectOffering('o1');
    enrollment.roster.calls.reset();
    c.remove(rosterRows[0]);
    expect(enrollment.drop).toHaveBeenCalledWith('r1');
    expect(enrollment.roster).toHaveBeenCalled(); // reload
  });

  it('remove aborts when the confirm is declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const c = make();
    c.selectOffering('o1');
    c.remove(rosterRows[0]);
    expect(enrollment.drop).not.toHaveBeenCalled();
  });

  it('exportCsv produces a downloadable blob', () => {
    const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    const c = make();
    c.selectOffering('o1');
    c.exportCsv();
    expect(createUrl).toHaveBeenCalled();
  });
});
