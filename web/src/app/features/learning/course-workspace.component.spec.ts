import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CourseWorkspaceComponent } from './course-workspace.component';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { LearningService } from '../../core/services/learning.service';
import { CourseContentService } from '../../core/services/course-content.service';
import { GradingService } from '../../core/services/grading.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import { WeekResponse } from '../../core/models/course-content.model';
import { AssignmentResponse } from '../../core/models/grading.model';
import { OfferingAccessResponse } from '../../core/models/learning.model';
import { ProfileResponse } from '../../core/models/user.model';
import { PagedResponse } from '../../core/models/pagination.model';

describe('CourseWorkspaceComponent', () => {
  let academic: jasmine.SpyObj<AcademicStructureService>;
  let learning: jasmine.SpyObj<LearningService>;
  let content: jasmine.SpyObj<CourseContentService>;
  let grading: jasmine.SpyObj<GradingService>;
  let auth: jasmine.SpyObj<AuthService>;
  const route = { snapshot: { paramMap: convertToParamMap({ offeringId: 'o1' }) } };

  const weeks = [
    { id: 'w1', weekNumber: 1, startDate: '2025-09-01', endDate: '2025-09-07', title: 'Week 1' },
    { id: 'w2', weekNumber: 2, startDate: '2025-10-01', endDate: '2025-10-07', title: 'Week 2' },
  ] as WeekResponse[];
  const assignments = [
    { id: 'a1', weekId: 'w1' },
    { id: 'a2', weekId: null },
  ] as unknown as AssignmentResponse[];

  function make(): CourseWorkspaceComponent {
    return TestBed.createComponent(CourseWorkspaceComponent).componentInstance;
  }

  beforeEach(() => {
    academic = jasmine.createSpyObj<AcademicStructureService>('AcademicStructureService', ['getCourseOffering']);
    learning = jasmine.createSpyObj<LearningService>('LearningService', ['access']);
    content = jasmine.createSpyObj<CourseContentService>('CourseContentService', ['listWeeks']);
    grading = jasmine.createSpyObj<GradingService>('GradingService', ['list']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);

    academic.getCourseOffering.and.returnValue(of({ id: 'o1', code: 'CS101' } as CourseOfferingResponse));
    learning.access.and.returnValue(of({ manage: true } as OfferingAccessResponse));
    content.listWeeks.and.returnValue(of(weeks));
    grading.list.and.returnValue(of({ content: assignments } as unknown as PagedResponse<AssignmentResponse>));
    auth.currentUser.and.returnValue({ role: 'LECTURER' } as ProfileResponse);

    route.snapshot.paramMap = convertToParamMap({ offeringId: 'o1' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AcademicStructureService, useValue: academic },
        { provide: LearningService, useValue: learning },
        { provide: CourseContentService, useValue: content },
        { provide: GradingService, useValue: grading },
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
  });

  it('loads offering, weeks, assignments and manage access on construction', () => {
    const c = make();
    expect(c.offering()?.id).toBe('o1');
    expect(c.weeks().length).toBe(2);
    expect(c.assignments().length).toBe(2);
    expect(learning.access).toHaveBeenCalledWith('o1'); // non-student => access checked
    expect(c.canManage()).toBeTrue();
  });

  it('weekViews merges assignments into their week by weekId', () => {
    const c = make();
    const views = c.weekViews();
    expect(views.find((v) => v.id === 'w1')?.assignments.map((a) => a.id)).toEqual(['a1']);
    expect(views.find((v) => v.id === 'w2')?.assignments).toEqual([]);
  });

  it('unscheduledAssignments are those with no weekId', () => {
    const c = make();
    expect(c.unscheduledAssignments().map((a) => a.id)).toEqual(['a2']);
  });

  it('groups weeks by month', () => {
    const c = make();
    expect(c.groupedByMonth().map((g) => g.month)).toEqual(['September 2025', 'October 2025']);
  });

  it('toggleWeek / isExpanded flip a week open and closed', () => {
    const c = make();
    const before = c.isExpanded('w1');
    c.toggleWeek('w1');
    expect(c.isExpanded('w1')).toBe(!before);
  });

  it('skips the access check for a student', () => {
    auth.currentUser.and.returnValue({ role: 'STUDENT' } as ProfileResponse);
    const c = make();
    expect(learning.access).not.toHaveBeenCalled();
    expect(c.isStudent()).toBeTrue();
  });
});
