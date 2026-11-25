import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LecturerAssignmentsComponent } from './lecturer-assignments.component';
import { GradingService } from '../../core/services/grading.service';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { AssignmentResponse } from '../../core/models/grading.model';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('LecturerAssignmentsComponent', () => {
  let grading: jasmine.SpyObj<GradingService>;
  let academic: jasmine.SpyObj<AcademicStructureService>;
  let auth: jasmine.SpyObj<AuthService>;

  const offerings = [{ id: 'o1', code: 'CS101', name: 'Intro', academicYearName: '2025/2026', academicYearStatus: 'ACTIVE' }] as CourseOfferingResponse[];
  const assignments = [
    { id: 'a1', title: 'Essay', courseCode: 'CS101', status: 'DRAFT', offeringId: 'o1', dueDate: new Date().toISOString(), maxMarks: 100 },
    { id: 'a2', title: 'Quiz', courseCode: 'CS101', status: 'OPEN', offeringId: 'o1', dueDate: new Date().toISOString(), maxMarks: 50 },
  ] as AssignmentResponse[];

  function listPage(items = assignments) {
    return of({ content: items, totalElements: items.length, totalPages: 1 } as unknown as PagedResponse<AssignmentResponse>);
  }
  function make(): LecturerAssignmentsComponent {
    return TestBed.createComponent(LecturerAssignmentsComponent).componentInstance;
  }

  beforeEach(() => {
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['list', 'create', 'update', 'setStatus', 'delete', 'uploadBrief', 'downloadBrief']);
    academic = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['listCourseOfferings']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);

    grading.list.and.returnValue(listPage());
    academic.listCourseOfferings.and.returnValue(of(offerings));
    auth.currentUser.and.returnValue({ role: 'LECTURER' } as ProfileResponse);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: GradingService, useValue: grading },
        { provide: AcademicStructureService, useValue: academic },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('loads offerings (mine for a lecturer) and assignments on construction', () => {
    const c = make();
    expect(academic.listCourseOfferings).toHaveBeenCalledWith({ mine: true });
    expect(c.assignments().length).toBe(2);
    expect(c.totalElements()).toBe(2);
  });

  it('filters within the loaded page by title/course text', () => {
    const c = make();
    c.titleFilter.set('quiz');
    expect(c.filtered().map((a) => a.id)).toEqual(['a2']);
  });

  it('counts active filters', () => {
    const c = make();
    expect(c.activeFilterCount()).toBe(0);
    c.onCourseFilterChange('o1');
    c.titleFilter.set('q');
    expect(c.activeFilterCount()).toBe(2);
  });

  it('openNew prepares a blank draft form', () => {
    const c = make();
    c.openNew();
    expect(c.editing()).toBe('new');
    expect(c.form.controls.offeringId.value).toBe('o1'); // defaults to first offering
    expect(c.form.controls.maxMarks.value).toBe(100);
  });

  it('openEdit loads the assignment and locks its offering', () => {
    const c = make();
    c.openEdit(assignments[1]);
    expect(c.editing()).toBe(assignments[1]);
    expect(c.form.controls.title.value).toBe('Quiz');
    expect(c.form.controls.offeringId.disabled).toBeTrue();
  });

  it('save creates a new draft and reloads', () => {
    grading.create.and.returnValue(of({ title: 'New' } as AssignmentResponse));
    const c = make();
    c.openNew();
    c.form.patchValue({ title: 'New', dueDate: '2026-01-01T10:00', maxMarks: 20 });
    grading.list.calls.reset();
    c.save();
    expect(grading.create).toHaveBeenCalled();
    expect(c.toast()?.message).toContain('created as a draft');
    expect(grading.list).toHaveBeenCalled();
  });

  it('save does nothing when the form is invalid', () => {
    const c = make();
    c.openNew();
    c.form.patchValue({ title: '' }); // required
    c.save();
    expect(grading.create).not.toHaveBeenCalled();
  });

  it('setStatus publishes and reports it', () => {
    grading.setStatus.and.returnValue(of(assignments[0]));
    const c = make();
    c.setStatus(assignments[0], 'OPEN'); // DRAFT -> OPEN == published
    expect(grading.setStatus).toHaveBeenCalledWith('a1', 'OPEN');
    expect(c.toast()?.message).toContain('published');
  });

  it('remove confirms then deletes', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    grading.delete.and.returnValue(of(void 0));
    const c = make();
    c.remove(assignments[0]);
    expect(grading.delete).toHaveBeenCalledWith('a1');
  });

  it('remove aborts when declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const c = make();
    c.remove(assignments[0]);
    expect(grading.delete).not.toHaveBeenCalled();
  });
});
