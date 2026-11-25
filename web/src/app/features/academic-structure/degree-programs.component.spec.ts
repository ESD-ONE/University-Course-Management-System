import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DegreeProgramsComponent } from './degree-programs.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { UserService } from '../../core/services/user.service';
import { RolesService } from '../../core/services/roles.service';
import { GradingService } from '../../core/services/grading.service';
import { AuthService } from '../../core/services/auth.service';
import { AcademicYearResponse, DegreeProgramAcademicYearResponse, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import { RoleResponse, ScopeHolderResponse } from '../../core/models/roles.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('DegreeProgramsComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;
  let userService: jasmine.SpyObj<UserService>;
  let rolesService: jasmine.SpyObj<RolesService>;
  let auth: jasmine.SpyObj<AuthService>;

  const assignments = [
    { id: 'asg1', degreeProgramId: 'p1', degreeProgramCode: 'BSC', degreeProgramName: 'BSc CS', durationYears: 4, totalCredits: 120, status: 'ACTIVE' },
    { id: 'asg2', degreeProgramId: 'p2', degreeProgramCode: 'MATH', degreeProgramName: 'BSc Math', durationYears: 3, totalCredits: 90, status: 'PLANNED' },
  ] as DegreeProgramAcademicYearResponse[];

  function make(): DegreeProgramsComponent {
    const c = TestBed.createComponent(DegreeProgramsComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listDegreePrograms', 'listAcademicYears', 'listDegreeProgramAcademicYears']);
    userService = jasmine.createSpyObj<UserService>('UserService', ['list']);
    rolesService = jasmine.createSpyObj<RolesService>('RolesService', ['roles', 'scopeHolders', 'setScopeHolder']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    const grading = jasmine.createSpyObj<GradingService>('GradingService', ['myGrades']);

    svc.listDegreePrograms.and.returnValue(of({ content: [{ id: 'p1', code: 'BSC', name: 'BSc CS' }] } as unknown as PagedResponse<DegreeProgramResponse>));
    svc.listAcademicYears.and.returnValue(of([{ id: 'y1', name: '2025/2026', status: 'ACTIVE', semesters: [] }] as unknown as AcademicYearResponse[]));
    svc.listDegreeProgramAcademicYears.and.returnValue(of(assignments));
    userService.list.and.returnValue(of({ content: [{ id: 'u1', name: 'Dr Who', email: 'who@x' }] } as unknown as PagedResponse<never>));
    rolesService.roles.and.returnValue(of([{ id: 'rh', name: 'Program Head', kind: 'LECTURER', permissions: [] }] as RoleResponse[]));
    rolesService.scopeHolders.and.returnValue(of([]));
    auth.currentUser.and.returnValue({ role: 'ADMIN' } as ProfileResponse);

    TestBed.configureTestingModule({
      providers: [
        { provide: AcademicStructureService, useValue: svc },
        { provide: UserService, useValue: userService },
        { provide: RolesService, useValue: rolesService },
        { provide: GradingService, useValue: grading },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('loads reference degrees, lecturers, years and year-assignments on init', () => {
    const c = make();
    expect(c.referenceDegrees().length).toBe(1);
    expect(c.lecturers().length).toBe(1);
    expect(c.allYears().length).toBe(1);
    expect(c.assignments().length).toBe(2);
    expect(c.loading()).toBeFalse();
  });

  it('filteredAssignments narrows by program and status', () => {
    const c = make();
    c.programFilter.set('p1');
    expect(c.filteredAssignments().map((a) => a.id)).toEqual(['asg1']);
    c.programFilter.set('');
    c.statusFilter.set('PLANNED');
    expect(c.filteredAssignments().map((a) => a.id)).toEqual(['asg2']);
  });

  it('counts active filters', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.programFilter.set('p1');
    c.statusFilter.set('ACTIVE');
    expect(c.activeFilterCount()).toBe(2);
  });

  it('assignableYears excludes archived years', () => {
    svc.listAcademicYears.and.returnValue(of([
      { id: 'y1', name: '2025/2026', status: 'ACTIVE', semesters: [] },
      { id: 'y0', name: '2019/2020', status: 'ARCHIVED', semesters: [] },
    ] as unknown as AcademicYearResponse[]));
    const c = make();
    expect(c.assignableYears().map((y) => y.id)).toEqual(['y1']);
  });

  it('openAssignHead seeds the head picker from the current holder', () => {
    const c = make();
    c.openAssignHead('p1', 'BSc CS');
    expect(c.assigningHeadFor()).toEqual({ programId: 'p1', programName: 'BSc CS' });
    expect(c.headPickerValue()).toBe('');
  });

  it('submitHead sets the program-head scope holder', () => {
    rolesService.setScopeHolder.and.returnValue(of({ scopeId: 'p1', userId: 'u1', userName: 'Dr Who' } as ScopeHolderResponse));
    const c = make();
    c.openAssignHead('p1', 'BSc CS');
    c.headPickerValue.set('u1');
    c.submitHead();
    expect(rolesService.setScopeHolder).toHaveBeenCalledWith('rh', 'DEGREE_PROGRAM', 'p1', 'u1');
    expect(c.programHeads().get('p1')?.userId).toBe('u1');
  });
});
