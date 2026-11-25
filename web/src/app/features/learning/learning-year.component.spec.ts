import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LearningYearComponent } from './learning-year.component';
import { LearningService } from '../../core/services/learning.service';
import { LearningTreeResponse } from '../../core/models/learning.model';

describe('LearningYearComponent', () => {
  let learning: jasmine.SpyObj<LearningService>;
  let router: Router;
  const route = { snapshot: { paramMap: convertToParamMap({ yearId: 'y1' }) } };

  const prog = (id: string, code: string, name: string) => ({ degreeProgramId: id, degreeProgramCode: code, degreeProgramName: name, offerings: [] });
  const tree = {
    years: [
      { academicYearId: 'y1', academicYearName: '2025/2026', programs: [prog('p1', 'BSC', 'BSc CS'), prog('p2', 'MATH', 'BSc Math')] },
      { academicYearId: 'y2', academicYearName: '2024/2025', programs: [] },
    ],
  } as unknown as LearningTreeResponse;

  function make(): LearningYearComponent {
    return TestBed.createComponent(LearningYearComponent).componentInstance;
  }

  beforeEach(() => {
    learning = jasmine.createSpyObj<LearningService>('LearningService', ['tree']);
    learning.tree.and.returnValue(of(tree));
    route.snapshot.paramMap = convertToParamMap({ yearId: 'y1' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: LearningService, useValue: learning },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('loads the year matching the route param', () => {
    const c = make();
    expect(c.yearId).toBe('y1');
    expect(c.year()?.academicYearId).toBe('y1');
    expect(c.programs().length).toBe(2);
  });

  it('filters the year’s programs by search', () => {
    const c = make();
    c.onSearchInput('bsc cs');
    expect(c.filteredPrograms().map((p) => p.degreeProgramId)).toEqual(['p1']);
  });

  it('navigates into a program within the year', () => {
    const c = make();
    c.openProgram({ degreeProgramId: 'p2' } as never);
    expect(router.navigate).toHaveBeenCalledWith(['/app/learning/years', 'y1', 'programs', 'p2']);
  });

  it('sets no year when the id is unknown', () => {
    route.snapshot.paramMap = convertToParamMap({ yearId: 'missing' });
    const c = make();
    expect(c.year()).toBeNull();
    expect(c.programs()).toEqual([]);
  });
});
