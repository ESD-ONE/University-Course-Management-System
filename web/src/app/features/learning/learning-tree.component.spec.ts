import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LearningTreeComponent } from './learning-tree.component';
import { LearningService } from '../../core/services/learning.service';
import { AuthService } from '../../core/services/auth.service';
import { LearningTreeResponse } from '../../core/models/learning.model';
import { ProfileResponse } from '../../core/models/user.model';

describe('LearningTreeComponent', () => {
  let learning: jasmine.SpyObj<LearningService>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  const prog = (id: string, code: string, name: string) => ({ degreeProgramId: id, degreeProgramCode: code, degreeProgramName: name, offerings: [] });
  const tree = {
    years: [
      { academicYearId: 'y1', academicYearName: '2025/2026', programs: [prog('p1', 'BSC', 'BSc CS'), prog('p2', 'MATH', 'BSc Math')] },
      { academicYearId: 'y2', academicYearName: '2024/2025', programs: [prog('p1', 'BSC', 'BSc CS')] },
    ],
  } as unknown as LearningTreeResponse;

  function make(): LearningTreeComponent {
    return TestBed.createComponent(LearningTreeComponent).componentInstance;
  }

  beforeEach(() => {
    learning = jasmine.createSpyObj<LearningService>('LearningService', ['tree']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    learning.tree.and.returnValue(of(tree));
    auth.currentUser.and.returnValue({ role: 'STUDENT' } as ProfileResponse);
    TestBed.configureTestingModule({
      providers: [
        { provide: LearningService, useValue: learning },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('loads the year tree on construction', () => {
    const c = make();
    expect(c.years().length).toBe(2);
    expect(c.loading()).toBeFalse();
  });

  it('deduplicates programs across years and sorts by code', () => {
    const c = make();
    expect(c.allPrograms().map((p) => p.degreeProgramId)).toEqual(['p1', 'p2']);
  });

  it('filters programs by search text', () => {
    const c = make();
    c.onSearchInput('math');
    expect(c.filteredPrograms().map((p) => p.degreeProgramId)).toEqual(['p2']);
    expect(c.activeFilterCount()).toBe(1);
  });

  it('paginates the program list', () => {
    const c = make();
    c.onPageSizeChange(1);
    expect(c.pagedPrograms().length).toBe(1);
    expect(c.totalPages()).toBe(2);
  });

  it('navigates to a year and a program', () => {
    const c = make();
    c.openYear('y1');
    expect(router.navigate).toHaveBeenCalledWith(['/app/learning/years', 'y1']);
    c.openProgram('p2');
    expect(router.navigate).toHaveBeenCalledWith(['/app/learning/programs', 'p2']);
  });

  it('records a load error', () => {
    learning.tree.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
  });
});
