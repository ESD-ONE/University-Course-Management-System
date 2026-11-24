import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseContentService } from './course-content.service';
import {
  CreateLinkItemRequest,
  CreateSessionRequest,
  CreateWeekRequest,
  UpdateWeekRequest,
} from '../models/course-content.model';

// Course content is served by the grading service, so its base is /api/grading.
describe('CourseContentService', () => {
  const base = '/api/grading';
  let service: CourseContentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CourseContentService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CourseContentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /offerings/:id/weeks', () => {
    service.listWeeks('o1').subscribe();
    httpMock.expectOne(`${base}/offerings/o1/weeks`).flush([]);
  });

  it('POST /offerings/:id/weeks', () => {
    const body = { title: 'Week 1' } as unknown as CreateWeekRequest;
    service.createWeek('o1', body).subscribe();
    const req = httpMock.expectOne(`${base}/offerings/o1/weeks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('PUT /weeks/:id', () => {
    service.updateWeek('w1', { title: 'Wk' } as unknown as UpdateWeekRequest).subscribe();
    const req = httpMock.expectOne(`${base}/weeks/w1`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('DELETE /weeks/:id', () => {
    service.deleteWeek('w1').subscribe();
    const req = httpMock.expectOne(`${base}/weeks/w1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POST /weeks/:id/sessions', () => {
    const body = { title: 'Lecture' } as unknown as CreateSessionRequest;
    service.createSession('w1', body).subscribe();
    const req = httpMock.expectOne(`${base}/weeks/w1/sessions`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('DELETE /sessions/:id', () => {
    service.deleteSession('s1').subscribe();
    const req = httpMock.expectOne(`${base}/sessions/s1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POST /content-items/link', () => {
    const body = { weekId: 'w1', url: 'http://x', title: 'Slides' } as unknown as CreateLinkItemRequest;
    service.createLinkItem(body).subscribe();
    const req = httpMock.expectOne(`${base}/content-items/link`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('POST /content-items/file builds a FormData with every provided field', () => {
    const file = new File(['x'], 'notes.pdf');
    service.uploadFileItem('w1', 'sess1', 'NOTE', 'Notes', 'desc', file).subscribe();
    const req = httpMock.expectOne(`${base}/content-items/file`);
    expect(req.request.body instanceof FormData).toBeTrue();
    const form = req.request.body as FormData;
    expect(form.get('weekId')).toBe('w1');
    expect(form.get('sessionId')).toBe('sess1');
    expect(form.get('type')).toBe('NOTE');
    expect(form.get('title')).toBe('Notes');
    expect(form.get('description')).toBe('desc');
    expect(form.get('file')).toBe(file);
    req.flush({});
  });

  it('POST /content-items/file omits optional sessionId/description when null', () => {
    const file = new File(['x'], 'notes.pdf');
    service.uploadFileItem('w1', null, 'NOTE', 'Notes', null, file).subscribe();
    const req = httpMock.expectOne(`${base}/content-items/file`);
    const form = req.request.body as FormData;
    expect(form.has('sessionId')).toBeFalse();
    expect(form.has('description')).toBeFalse();
    req.flush({});
  });

  it('PATCH /content-items/:id/status', () => {
    service.setItemStatus('i1', 'PUBLISHED').subscribe();
    const req = httpMock.expectOne(`${base}/content-items/i1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'PUBLISHED' });
    req.flush({});
  });

  it('DELETE /content-items/:id', () => {
    service.deleteItem('i1').subscribe();
    const req = httpMock.expectOne(`${base}/content-items/i1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('GET /content-items/:id/file requests a blob', () => {
    service.downloadItem('i1').subscribe();
    const req = httpMock.expectOne(`${base}/content-items/i1/file`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });
});
