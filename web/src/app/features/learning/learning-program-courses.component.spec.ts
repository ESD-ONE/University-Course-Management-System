import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LearningProgramCoursesComponent } from './learning-program-courses.component';
import { LearningService } from '../../core/services/learning.service';
import { LearningTreeResponse } from '../../core/models/learning.model';

describe('LearningProgramCoursesComponent', () => {
  let learning: jasmine.SpyObj<LearningService>;
  let router: Router;
  const route = { snapshot: { paramMap: convertToParamMap({ yearId: 'y1', programId: 'p1' }) } };

  const offering = (id: string, code: string, name: string, status: string) => ({ offeringId: id, code, name, status });
  const program = {
    degreeProgramId: 'p1', degreeProgramCode: 'BSC', degreeProgramName: 'BSc CS',
    offerings: [offering('o1', 'CS101', 'Intro', 'PUBLISHED'), offering('o2', 'CS102', 'Data', 'DRAFT')],
  };
  const tree = {
    years: [
      { academicYearId: 'y1', academicYearName: '2025/2026', programs: [program] },
      { academicYearId: 'y2', academicYearName: '2024/2025', programs: [{ ...program, offerings: [offering('o3', 'CS201', 'Algo', 'PUBLISHED')] }] },
    ],
  } as unknown as LearningTreeResponse;

  function make(): LearningProgramCoursesComponent {
    return TestBed.createComponent(LearningProgramCoursesComponent).componentInstance;
  }

  function configure(params: Record<string, string>) {
    route.snapshot.paramMap = convertToParamMap(params);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: LearningService, useValue: learning },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  }

  beforeEach(() => {
    learning = jasmine.createSpyObj<LearningService>('LearningService', ['tree']);
    learning.tree.and.returnValue(of(tree));
  });

  it('with a yearId, shows that year’s program courses', () => {
    configure({ yearId: 'y1', programId: 'p1' });
    const c = make();
    expect(c.programCode()).toBe('BSC');
    expect(c.courses().length).toBe(2);
  });

  it('filters courses by search, status and year', () => {
    configure({ yearId: 'y1', programId: 'p1' });
    const c = make();
    c.onStatusFilterChange('PUBLISHED');
    expect(c.filteredCourses().map((o) => o.offeringId)).toEqual(['o1']);
    c.onStatusFilterChange('');
    c.onSearchInput('data');
    expect(c.filteredCourses().map((o) => o.offeringId)).toEqual(['o2']);
  });

  it('with no yearId, merges offerings across every year the program appears in', () => {
    configure({ programId: 'p1' });
    const c = make();
    expect(c.programName()).toBe('BSc CS');
    expect(c.courses().length).toBe(3); // o1, o2 (y1) + o3 (y2)
    expect(c.yearOptions().map((o) => o.value).sort()).toEqual(['y1', 'y2']);
  });

  it('opens an offering workspace', () => {
    configure({ yearId: 'y1', programId: 'p1' });
    const c = make();
    c.openOffering('o1');
    expect(router.navigate).toHaveBeenCalledWith(['/app/learning/offerings', 'o1']);
  });
});
