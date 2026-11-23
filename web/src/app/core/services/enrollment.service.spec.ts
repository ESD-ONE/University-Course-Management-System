import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnrollmentService } from './enrollment.service';
import { CreateProgramEnrollmentRequest, EnrollRequest } from '../models/enrollment.model';

describe('EnrollmentService', () => {
  const base = '/api/enrollment';
  let service: EnrollmentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EnrollmentService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EnrollmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /catalog without a year has no params', () => {
    service.catalog().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/catalog`);
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('GET /catalog forwards academicYearId', () => {
    service.catalog('y1').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/catalog`);
    expect(req.request.params.get('academicYearId')).toBe('y1');
    req.flush([]);
  });

  it('POST /course-enrollments', () => {
    const body = { offeringId: 'o1' } as unknown as EnrollRequest;
    service.enroll(body).subscribe();
    const req = httpMock.expectOne(`${base}/course-enrollments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('DELETE /course-enrollments/:id', () => {
    service.drop('e1').subscribe();
    const req = httpMock.expectOne(`${base}/course-enrollments/e1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('GET /course-enrollments/me', () => {
    service.myEnrollments().subscribe();
    httpMock.expectOne(`${base}/course-enrollments/me`).flush([]);
  });

  it('GET /course-enrollments roster with pagination defaults', () => {
    service.roster('o1').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/course-enrollments`);
    expect(req.request.params.get('offeringId')).toBe('o1');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ content: [] });
  });

  it('GET /course-enrollments roster forwards status', () => {
    service.roster('o1', { status: 'ENROLLED', page: 1, size: 5 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/course-enrollments`);
    expect(req.request.params.get('status')).toBe('ENROLLED');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [] });
  });

  it('GET /offerings/seats appends each id', () => {
    service.seats(['o1', 'o2', 'o3']).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/offerings/seats`);
    expect(req.request.params.getAll('ids')).toEqual(['o1', 'o2', 'o3']);
    req.flush([]);
  });

  it('POST /program-enrollments', () => {
    const body = { studentId: 's1', degreeProgramId: 'p1' } as unknown as CreateProgramEnrollmentRequest;
    service.registerProgram(body).subscribe();
    const req = httpMock.expectOne(`${base}/program-enrollments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('GET /program-enrollments/me', () => {
    service.myProgramEnrollments().subscribe();
    httpMock.expectOne(`${base}/program-enrollments/me`).flush([]);
  });

  it('PATCH /program-enrollments/:id', () => {
    service.updateProgramEnrollment('pe1', { currentYearOfStudy: 2, status: 'ACTIVE' }).subscribe();
    const req = httpMock.expectOne(`${base}/program-enrollments/pe1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ currentYearOfStudy: 2, status: 'ACTIVE' });
    req.flush({});
  });

  it('GET /students forwards q', () => {
    service.searchStudents('ann').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/students`);
    expect(req.request.params.get('q')).toBe('ann');
    req.flush([]);
  });

  it('GET /registrations forwards all filters', () => {
    service.registrations({ studentId: 's1', targetType: 'COURSE', action: 'ENROLL', page: 2 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/registrations`);
    expect(req.request.params.get('studentId')).toBe('s1');
    expect(req.request.params.get('targetType')).toBe('COURSE');
    expect(req.request.params.get('action')).toBe('ENROLL');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ content: [] });
  });

  it('DELETE /registrations/:id', () => {
    service.deleteRegistration('r1').subscribe();
    const req = httpMock.expectOne(`${base}/registrations/r1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
