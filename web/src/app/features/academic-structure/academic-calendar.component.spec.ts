import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AcademicCalendarComponent } from './academic-calendar.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AcademicYearResponse, SemesterResponse } from '../../core/models/academic-structure.model';

describe('AcademicCalendarComponent', () => {
  let svc: jasmine.SpyObj<AcademicStructureService>;

  const year = { id: 'y1', name: '2025/2026', status: 'ACTIVE', startDate: null, endDate: null, semesters: [] as SemesterResponse[] } as AcademicYearResponse;

  function make(): AcademicCalendarComponent {
    const c = TestBed.createComponent(AcademicCalendarComponent).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', [
      'listAcademicYears', 'createAcademicYear', 'updateAcademicYear', 'deleteAcademicYear', 'addSemester', 'updateSemester', 'deleteSemester',
    ]);
    svc.listAcademicYears.and.returnValue(of([year]));
    TestBed.configureTestingModule({ providers: [{ provide: AcademicStructureService, useValue: svc }] });
  });

  it('loads years on init', () => {
    const c = make();
    expect(c.years().length).toBe(1);
    expect(c.loading()).toBeFalse();
  });

  it('selectedYear falls back to the first year', () => {
    const c = make();
    expect(c.selectedYear()?.id).toBe('y1');
  });

  it('openYearDialog opens a blank create form', () => {
    const c = make();
    c.openYearDialog();
    expect(c.showYearDialog()).toBeTrue();
    expect(c.editingYearId()).toBeNull();
    expect(c.yearForm.controls.name.value).toBe('');
  });

  it('submitYear rejects a blank name', () => {
    const c = make();
    c.openYearDialog();
    c.submitYear();
    expect(c.dialogError()).toContain('name');
    expect(svc.createAcademicYear).not.toHaveBeenCalled();
  });

  it('submitYear creates a year and prepends it', () => {
    const created = { ...year, id: 'y2', name: '2026/2027' };
    svc.createAcademicYear.and.returnValue(of(created));
    const c = make();
    c.openYearDialog();
    c.yearForm.patchValue({ name: '2026/2027' });
    c.submitYear();
    expect(svc.createAcademicYear).toHaveBeenCalled();
    expect(c.years()[0].id).toBe('y2');
    expect(c.showYearDialog()).toBeFalse();
  });

  it('deleteYear confirms then removes', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    svc.deleteAcademicYear.and.returnValue(of(void 0));
    const c = make();
    c.deleteYear(year);
    expect(svc.deleteAcademicYear).toHaveBeenCalledWith('y1');
    expect(c.years().length).toBe(0);
  });

  it('deleteYear aborts when declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const c = make();
    c.deleteYear(year);
    expect(svc.deleteAcademicYear).not.toHaveBeenCalled();
  });

  it('submitSemester adds a semester to the selected year', () => {
    const created = { id: 's1', name: 'Semester 1', sequence: 1, status: 'UPCOMING' } as SemesterResponse;
    svc.addSemester.and.returnValue(of(created));
    const c = make();
    c.openSemesterDialog();
    c.semesterForm.patchValue({ name: 'Semester 1', sequence: 1 });
    c.submitSemester();
    expect(svc.addSemester).toHaveBeenCalledWith('y1', jasmine.objectContaining({ name: 'Semester 1' }));
    expect(c.years()[0].semesters.length).toBe(1);
  });
});
