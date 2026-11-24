import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LearningService } from './learning.service';
import { AuthService } from './auth.service';
import { ProfileResponse, RoleKind } from '../models/user.model';

/**
 * LearningService picks its endpoint from the signed-in user's role: students read the
 * enrollment service's tree, staff read academic-structure's. That branch is the only
 * logic here, so it's what these tests pin.
 */
describe('LearningService', () => {
  const academic = '/api/academic';
  const enrollment = '/api/enrollment';
  let service: LearningService;
  let httpMock: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  function asRole(role: RoleKind) {
    auth.currentUser.and.returnValue({ role } as ProfileResponse);
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    TestBed.configureTestingModule({
      providers: [
        LearningService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(LearningService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('tree() hits the enrollment tree for a STUDENT', () => {
    asRole('STUDENT');
    service.tree().subscribe();
    httpMock.expectOne(`${enrollment}/learning/tree`).flush({});
  });

  it('tree() hits the academic-structure tree for a LECTURER', () => {
    asRole('LECTURER');
    service.tree().subscribe();
    httpMock.expectOne(`${academic}/course-offerings/learning-tree`).flush({});
  });

  it('tree() hits the academic-structure tree for an ADMIN', () => {
    asRole('ADMIN');
    service.tree().subscribe();
    httpMock.expectOne(`${academic}/course-offerings/learning-tree`).flush({});
  });

  it('access() always hits academic-structure', () => {
    service.access('o1').subscribe();
    const req = httpMock.expectOne(`${academic}/course-offerings/o1/access`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
