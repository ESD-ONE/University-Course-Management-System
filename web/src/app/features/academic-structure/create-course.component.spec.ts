import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { CreateCourseComponent } from './create-course.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseCatalogResponse, CourseResponse, DegreeProgramResponse } from '../../core/models/academic-structure.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('CreateCourseComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  const catalog = [{ id: 'cat1', code: 'CS101', name: 'Intro', defaultCredits: 4 }] as CourseCatalogResponse[];
  const programs = [{ id: 'p1', code: 'BSC', name: 'BSc CS', departmentId: 'd1' }] as DegreeProgramResponse[];

  function page<T>(content: T[]) { return of({ content } as unknown as PagedResponse<T>); }
  function make(): CreateCourseComponent {
    const c = TestBed.createComponent(CreateCourseComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listCourseCatalog', 'listDegreePrograms', 'createCourse', 'createCourseCatalogEntry']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    svc.listCourseCatalog.and.returnValue(page(catalog));
    svc.listDegreePrograms.and.returnValue(page(programs));
    auth.currentUser.and.returnValue({ role: 'ADMIN' } as ProfileResponse);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AcademicStructureService, useValue: svc },
        { provide: AuthService, useValue: auth },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  it('loads catalog + programs and defaults the program', () => {
    const c = make();
    expect(c.catalog().length).toBe(1);
    expect(c.degreePrograms().length).toBe(1);
    expect(c.form.controls.degreeProgramId.value).toBe('p1');
    expect(c.loadingOptions()).toBeFalse();
  });

  it('admin back link points to /app/courses', () => {
    const c = make();
    expect(c.backLink()).toBe('/app/courses');
  });

  it('onCodeInput matches an existing catalog subject and fills name/credits', () => {
    const c = make();
    c.onCodeInput('cs101');
    expect(c.matchedCatalog()?.id).toBe('cat1');
    expect(c.form.controls.courseName.value).toBe('Intro');
    expect(c.form.controls.defaultCredits.value).toBe(4);
  });

  it('clearCode unlocks the name/credits it had filled', () => {
    const c = make();
    c.onCodeInput('cs101');
    c.clearCode(new MouseEvent('click'));
    expect(c.matchedCatalog()).toBeNull();
    expect(c.form.controls.courseName.value).toBe('');
  });

  it('save rejects an invalid form', () => {
    const c = make();
    c.form.patchValue({ courseCode: '', courseName: '', beginDate: '' });
    c.save(false);
    expect(c.error()).toContain('Fill in');
    expect(svc.createCourse).not.toHaveBeenCalled();
  });

  it('save reuses a matched catalog subject (no new catalog entry)', () => {
    svc.createCourse.and.returnValue(of({ id: 'course1' } as CourseResponse));
    const c = make();
    c.onCodeInput('CS101'); // matched -> matchedCatalog set
    c.form.patchValue({ courseCode: 'CS101', beginDate: '2025-09-01', yearOfStudy: 1, capacity: 40 });
    c.save(true);
    expect(svc.createCourseCatalogEntry).not.toHaveBeenCalled();
    expect(svc.createCourse).toHaveBeenCalledWith(jasmine.objectContaining({ catalogCourseId: 'cat1', status: 'PUBLISHED' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/courses');
  });

  it('save creates a new catalog subject first when the code is new', () => {
    svc.createCourseCatalogEntry.and.returnValue(of({ id: 'cat2', code: 'CS999', name: 'New', defaultCredits: 3 } as CourseCatalogResponse));
    svc.createCourse.and.returnValue(of({ id: 'course2' } as CourseResponse));
    const c = make();
    c.form.patchValue({ courseCode: 'cs999', courseName: 'New Course', defaultCredits: 3, beginDate: '2025-09-01', yearOfStudy: 1, capacity: 40 });
    c.save(false);
    expect(svc.createCourseCatalogEntry).toHaveBeenCalledWith(jasmine.objectContaining({ code: 'CS999', owningDepartmentId: 'd1' }));
    expect(svc.createCourse).toHaveBeenCalledWith(jasmine.objectContaining({ catalogCourseId: 'cat2', status: 'DRAFT' }));
  });
});
