import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReferenceDegreeDetailsComponent } from './reference-degree-details.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { ReferenceDataService } from '../../core/services/reference-data.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseCatalogResponse, DegreeProgramResponse, ProgramCourseResponse } from '../../core/models/academic-structure.model';
import { DepartmentResponse } from '../../core/models/reference-data.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('ReferenceDegreeDetailsComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;
  let refData: jasmine.SpyObj<ReferenceDataService>;
  let auth: jasmine.SpyObj<AuthService>;

  const program = { id: 'p1', code: 'BSC', name: 'BSc CS', departmentId: 'd1', durationYears: 4, totalCredits: 120, status: 'ACTIVE' } as DegreeProgramResponse;
  const curriculum = [
    { id: 'pc1', requirementType: 'CORE', catalogCourseCode: 'CS101' },
    { id: 'pc2', requirementType: 'ELECTIVE', catalogCourseCode: 'CS201' },
  ] as ProgramCourseResponse[];
  const catalog = [{ id: 'cat1', code: 'CS101', name: 'Intro', defaultCredits: 4 }] as CourseCatalogResponse[];

  function make(): ReferenceDegreeDetailsComponent {
    const c = TestBed.createComponent(ReferenceDegreeDetailsComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', [
      'listCourseCatalog', 'listDegreePrograms', 'listCurriculum', 'createDegreeProgram', 'updateDegreeProgram', 'deleteDegreeProgram', 'addCurriculumEntry',
    ]);
    refData = jasmine.createSpyObj<ReferenceDataService>('ReferenceDataService', ['listDepartments']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);

    refData.listDepartments.and.returnValue(of({ content: [{ id: 'd1', name: 'CS', facultyName: 'Eng' }] } as unknown as PagedResponse<DepartmentResponse>));
    svc.listCourseCatalog.and.returnValue(of({ content: catalog } as unknown as PagedResponse<CourseCatalogResponse>));
    svc.listDegreePrograms.and.returnValue(of({ content: [program], number: 0, totalElements: 1, totalPages: 1 } as unknown as PagedResponse<DegreeProgramResponse>));
    svc.listCurriculum.and.returnValue(of(curriculum));
    auth.currentUser.and.returnValue({ role: 'ADMIN' } as ProfileResponse);

    TestBed.configureTestingModule({
      providers: [
        { provide: AcademicStructureService, useValue: svc },
        { provide: ReferenceDataService, useValue: refData },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('loads departments, catalog, programs, and selects the first program’s curriculum', () => {
    const c = make();
    expect(c.departments().length).toBe(1);
    expect(c.catalog().length).toBe(1);
    expect(c.programs().length).toBe(1);
    expect(c.selectedProgram()?.id).toBe('p1');
    expect(c.curriculum().length).toBe(2);
  });

  it('totals core and elective curriculum entries', () => {
    const c = make();
    expect(c.totalCore()).toBe(1);
    expect(c.totalElective()).toBe(1);
  });

  it('openProgramDialog opens a blank create form defaulting the department', () => {
    const c = make();
    c.openProgramDialog();
    expect(c.showProgramDialog()).toBeTrue();
    expect(c.editingProgramId()).toBeNull();
    expect(c.programForm.controls.departmentId.value).toBe('d1');
  });

  it('submitProgram creates a reference degree', () => {
    svc.createDegreeProgram.and.returnValue(of({ ...program, id: 'p2', name: 'BSc Math' }));
    const c = make();
    c.openProgramDialog();
    c.programForm.patchValue({ code: 'math', name: 'BSc Math', departmentId: 'd1' });
    c.submitProgram();
    expect(svc.createDegreeProgram).toHaveBeenCalledWith(jasmine.objectContaining({ code: 'MATH', name: 'BSc Math' }));
    expect(c.showProgramDialog()).toBeFalse();
  });

  it('submitProgram rejects an invalid form', () => {
    const c = make();
    c.openProgramDialog();
    c.programForm.patchValue({ code: '', name: '' });
    c.submitProgram();
    expect(c.dialogError()).toContain('program code');
    expect(svc.createDegreeProgram).not.toHaveBeenCalled();
  });

  it('deleteProgram confirms then deletes', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    svc.deleteDegreeProgram.and.returnValue(of(void 0));
    const c = make();
    c.deleteProgram(program);
    expect(svc.deleteDegreeProgram).toHaveBeenCalledWith('p1');
  });

  it('submitCurriculum adds a catalog course to the selected program', () => {
    svc.addCurriculumEntry.and.returnValue(of({ id: 'pc3', requirementType: 'CORE', catalogCourseCode: 'CS101' } as ProgramCourseResponse));
    const c = make();
    c.openCurriculumDialog();
    c.submitCurriculum();
    expect(svc.addCurriculumEntry).toHaveBeenCalledWith('p1', jasmine.objectContaining({ catalogCourseId: 'cat1' }));
    expect(c.curriculum().some((x) => x.id === 'pc3')).toBeTrue();
  });
});
