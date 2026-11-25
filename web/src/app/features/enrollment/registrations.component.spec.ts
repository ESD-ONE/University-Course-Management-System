import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { RegistrationsComponent } from './registrations.component';
import { EnrollmentService } from '../../core/services/enrollment.service';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfileResponse, RoleKind } from '../../core/models/user.model';
import { ProgramEnrollmentResponse, RegistrationResponse, StudentOption, CourseEnrollmentResponse } from '../../core/models/enrollment.model';
import { AcademicYearResponse, CourseOfferingResponse, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('RegistrationsComponent', () => {
  let enrollment: jasmine.SpyObj<EnrollmentService>;
  let academic: jasmine.SpyObj<AcademicStructureService>;
  let auth: jasmine.SpyObj<AuthService>;

  const student = { id: 's1', name: 'Ada', studentNumber: 'S1', email: 'ada@x', activeProgramName: null, activeProgramId: null } as unknown as StudentOption;
  const years = [{ id: 'y1', name: '2025/2026', status: 'ACTIVE' }] as AcademicYearResponse[];
  const programs = [{ id: 'p1', name: 'BSc CS', code: 'CS', durationYears: 4 }] as DegreeProgramResponse[];
  const offerings = [{ id: 'o1', code: 'CS101', name: 'Intro', academicYearStatus: 'ACTIVE', degreeProgramId: 'p1', yearOfStudy: 1, academicYearName: '2025/2026' }] as CourseOfferingResponse[];

  function setRole(role: RoleKind) {
    auth.currentUser.and.returnValue({ role } as ProfileResponse);
  }
  function make(): RegistrationsComponent {
    return TestBed.createComponent(RegistrationsComponent).componentInstance;
  }

  beforeEach(() => {
    enrollment = jasmine.createSpyObj<EnrollmentService>('EnrollmentService', [
      'searchStudents', 'registerProgram', 'enroll', 'registrations', 'deleteRegistration', 'roster', 'programEnrollments', 'updateProgramEnrollment', 'drop',
    ]);
    academic = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listAcademicYears', 'listDegreePrograms', 'listCourseOfferings']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);

    enrollment.searchStudents.and.returnValue(of([student]));
    enrollment.registrations.and.returnValue(of({ content: [], number: 0, totalElements: 0, totalPages: 0 } as unknown as PagedResponse<RegistrationResponse>));
    academic.listAcademicYears.and.returnValue(of(years));
    academic.listDegreePrograms.and.returnValue(of({ content: programs } as unknown as PagedResponse<DegreeProgramResponse>));
    academic.listCourseOfferings.and.returnValue(of(offerings));
    setRole('ADMIN');

    TestBed.configureTestingModule({
      providers: [
        { provide: EnrollmentService, useValue: enrollment },
        { provide: AcademicStructureService, useValue: academic },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('defaults an admin to the Program tab and loads reference data', () => {
    const c = make();
    expect(c.isAdmin()).toBeTrue();
    expect(c.tab()).toBe('program');
    expect(c.years().length).toBe(1);
    expect(c.intakeYearId()).toBe('y1');
    expect(c.programs().length).toBe(1);
    expect(c.programId()).toBe('p1');
    expect(c.offerings().length).toBe(1);
  });

  it('defaults a lecturer to the Course tab and skips the admin-only program list', () => {
    setRole('LECTURER');
    const c = make();
    expect(c.tab()).toBe('course');
    expect(academic.listDegreePrograms).not.toHaveBeenCalled();
  });

  it('canSubmit requires a student + program + intake year on the Program tab', () => {
    const c = make();
    expect(c.canSubmit()).toBeFalse(); // no student yet
    c.onStudentChange('s1');
    expect(c.canSubmit()).toBeTrue(); // program + year came from load
  });

  it('canSubmit on the Course tab requires an offering', () => {
    const c = make();
    c.onStudentChange('s1');
    c.setTab('course');
    expect(c.canSubmit()).toBeFalse();
    c.offeringId.set('o1');
    expect(c.canSubmit()).toBeTrue();
  });

  it('onStudentChange selects the student and clears any chosen offering', fakeAsync(() => {
    const c = make();
    tick(300); // let the constructor's initial student search populate students()
    c.offeringId.set('o1');
    c.onStudentChange('s1');
    expect(c.studentId()).toBe('s1');
    expect(c.selectedStudent()?.id).toBe('s1');
    expect(c.offeringId()).toBe('');
  }));

  it('registerProgram posts the registration and resets step 1 on success', () => {
    enrollment.registerProgram.and.returnValue(of({ studentName: 'Ada', degreeProgramName: 'BSc CS' } as ProgramEnrollmentResponse));
    const c = make();
    c.onStudentChange('s1');
    c.registerProgram();
    expect(enrollment.registerProgram).toHaveBeenCalledWith(jasmine.objectContaining({ studentId: 's1', degreeProgramId: 'p1', intakeAcademicYearId: 'y1' }));
    expect(c.toast()?.message).toContain('registered in BSc CS');
    expect(c.studentId()).toBe(''); // reset for the next student
  });

  it('registerProgram surfaces a server error', () => {
    enrollment.registerProgram.and.returnValue(throwError(() => new HttpErrorResponse({ error: { message: 'Already registered' }, status: 409 })));
    const c = make();
    c.onStudentChange('s1');
    c.registerProgram();
    expect(c.formError()).toBe('Already registered');
  });

  it('registerCourse offers the waitlist on COURSE_FULL', () => {
    enrollment.enroll.and.returnValue(throwError(() => new HttpErrorResponse({ error: { code: 'COURSE_FULL' }, status: 409 })));
    const c = make();
    c.onStudentChange('s1');
    c.setTab('course');
    c.offeringId.set('o1');
    c.registerCourse();
    expect(c.waitlistOffer()).toBeTrue();
  });

  it('registerCourse success reports enrollment', () => {
    enrollment.enroll.and.returnValue(of({ status: 'ENROLLED', courseCode: 'CS101' } as CourseEnrollmentResponse));
    const c = make();
    c.onStudentChange('s1');
    c.setTab('course');
    c.offeringId.set('o1');
    c.registerCourse();
    expect(c.toast()?.message).toContain('Registered in CS101');
    expect(c.offeringId()).toBe('');
  });

  it('toggleRecent lazily loads the log the first time it is opened', () => {
    const c = make();
    expect(enrollment.registrations).not.toHaveBeenCalled();
    c.toggleRecent();
    expect(c.recentExpanded()).toBeTrue();
    expect(enrollment.registrations).toHaveBeenCalledTimes(1);
    c.toggleRecent(); // collapse
    c.toggleRecent(); // re-open — already loaded once, no duplicate initial load
    expect(enrollment.registrations).toHaveBeenCalledTimes(1);
  });

  it('onStudentQuery debounces the student search', fakeAsync(() => {
    const c = make();
    enrollment.searchStudents.calls.reset();
    c.onStudentQuery('ad');
    expect(enrollment.searchStudents).not.toHaveBeenCalled();
    tick(300);
    expect(enrollment.searchStudents).toHaveBeenCalledWith('ad');
  }));

  it('requestDelete / cancelDelete toggle the confirm target', () => {
    const c = make();
    const reg = { id: 'reg1', studentName: 'Ada' } as RegistrationResponse;
    c.requestDelete(reg);
    expect(c.confirmTarget()).toBe(reg);
    c.cancelDelete();
    expect(c.confirmTarget()).toBeNull();
  });
});
