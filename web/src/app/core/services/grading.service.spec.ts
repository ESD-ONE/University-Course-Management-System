import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GradingService } from './grading.service';
import { AssignmentRequest } from '../models/grading.model';

describe('GradingService', () => {
  const base = '/api/grading';
  let service: GradingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GradingService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GradingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /assignments with pagination defaults', () => {
    service.list().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/assignments`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('offeringId')).toBeFalse();
    req.flush({ content: [] });
  });

  it('GET /assignments forwards offeringId + status', () => {
    service.list({ offeringId: 'o1', status: 'OPEN' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/assignments`);
    expect(req.request.params.get('offeringId')).toBe('o1');
    expect(req.request.params.get('status')).toBe('OPEN');
    req.flush({ content: [] });
  });

  it('GET /assignments/:id', () => {
    service.get('a1').subscribe();
    httpMock.expectOne(`${base}/assignments/a1`).flush({});
  });

  it('POST /assignments', () => {
    const body = { title: 'HW1' } as unknown as AssignmentRequest;
    service.create(body).subscribe();
    const req = httpMock.expectOne(`${base}/assignments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('PUT /assignments/:id', () => {
    const body = { title: 'HW1' } as unknown as AssignmentRequest;
    service.update('a1', body).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('PATCH /assignments/:id/status', () => {
    service.setStatus('a1', 'CLOSED').subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'CLOSED' });
    req.flush({});
  });

  it('DELETE /assignments/:id', () => {
    service.delete('a1').subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POST /assignments/:id/brief sends a FormData with the file', () => {
    const file = new File(['x'], 'brief.pdf');
    service.uploadBrief('a1', file).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/brief`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({});
  });

  it('GET /assignments/:id/brief requests a blob', () => {
    service.downloadBrief('a1').subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/brief`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('GET /assignments/:id/submissions', () => {
    service.submissions('a1').subscribe();
    httpMock.expectOne(`${base}/assignments/a1/submissions`).flush([]);
  });

  it('POST /assignments/:id/submissions sends FormData', () => {
    const file = new File(['x'], 's.pdf');
    service.submit('a1', file).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/submissions`);
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({});
  });

  it('GET /submissions/:id/file requests a blob', () => {
    service.downloadSubmission('sub1').subscribe();
    const req = httpMock.expectOne(`${base}/submissions/sub1/file`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('PUT /submissions/:id/grade', () => {
    service.grade('sub1', 85, 'good').subscribe();
    const req = httpMock.expectOne(`${base}/submissions/sub1/grade`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ marks: 85, feedback: 'good' });
    req.flush({});
  });

  it('PUT /assignments/:id/grades (batch)', () => {
    const grades = [{ submissionId: 'sub1', marks: 90, feedback: null }];
    service.gradeAll('a1', grades).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/grades`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ grades });
    req.flush([]);
  });

  it('GET /grades/me', () => {
    service.myGrades().subscribe();
    httpMock.expectOne(`${base}/grades/me`).flush({});
  });

  it('PATCH /assignments/:id/grades-visibility', () => {
    service.setGradesHidden('a1', true).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/grades-visibility`);
    expect(req.request.body).toEqual({ hidden: true });
    req.flush({});
  });

  it('PATCH /assignments/:id/grades/release', () => {
    service.releaseGrades('a1').subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a1/grades/release`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });

  it('GET /stats/offering-analytics forwards offeringId', () => {
    service.offeringAnalytics('o1').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/stats/offering-analytics`);
    expect(req.request.params.get('offeringId')).toBe('o1');
    req.flush({});
  });
});
