import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReferenceDataService } from './reference-data.service';
import {
  CreateCourseCategoryRequest,
  CreateDepartmentRequest,
  CreateFacultyRequest,
  CreateGradingScaleRequest,
  UpdateFacultyRequest,
} from '../models/reference-data.model';

describe('ReferenceDataService', () => {
  const base = '/api/base';
  let service: ReferenceDataService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ReferenceDataService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReferenceDataService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // --- Faculties: also exercises the shared queryParams() helper ---
  it('GET /faculties with pagination defaults', () => {
    service.listFaculties().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/faculties`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ content: [] });
  });

  it('GET /faculties forwards status + q', () => {
    service.listFaculties({ status: 'ACTIVE', q: 'eng', page: 3, size: 50 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/faculties`);
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('q')).toBe('eng');
    expect(req.request.params.get('page')).toBe('3');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [] });
  });

  it('GET /faculties/counts', () => {
    service.facultyCounts().subscribe();
    httpMock.expectOne(`${base}/faculties/counts`).flush({});
  });

  it('POST /faculties', () => {
    const body = { name: 'Engineering' } as unknown as CreateFacultyRequest;
    service.createFaculty(body).subscribe();
    const req = httpMock.expectOne(`${base}/faculties`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('PUT /faculties/:id', () => {
    service.updateFaculty('f1', { name: 'Eng' } as unknown as UpdateFacultyRequest).subscribe();
    const req = httpMock.expectOne(`${base}/faculties/f1`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('DELETE /faculties/:id', () => {
    service.deleteFaculty('f1').subscribe();
    const req = httpMock.expectOne(`${base}/faculties/f1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  // --- Departments: the one list that also forwards facultyId ---
  it('GET /departments forwards facultyId alongside the shared params', () => {
    service.listDepartments('f1', { q: 'cs' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/departments`);
    expect(req.request.params.get('facultyId')).toBe('f1');
    expect(req.request.params.get('q')).toBe('cs');
    req.flush({ content: [] });
  });

  it('GET /departments omits facultyId when not given', () => {
    service.listDepartments(undefined).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/departments`);
    expect(req.request.params.has('facultyId')).toBeFalse();
    req.flush({ content: [] });
  });

  it('POST /departments', () => {
    const body = { name: 'CS', facultyId: 'f1' } as unknown as CreateDepartmentRequest;
    service.createDepartment(body).subscribe();
    const req = httpMock.expectOne(`${base}/departments`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  // --- Grading scales + course categories: confirm the routing ---
  it('GET /grading-scales', () => {
    service.listGradingScales().subscribe();
    httpMock.expectOne((r) => r.url === `${base}/grading-scales`).flush({ content: [] });
  });

  it('POST /grading-scales', () => {
    service.createGradingScale({ name: 'Std' } as unknown as CreateGradingScaleRequest).subscribe();
    const req = httpMock.expectOne(`${base}/grading-scales`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('GET /course-categories/counts', () => {
    service.courseCategoryCounts().subscribe();
    httpMock.expectOne(`${base}/course-categories/counts`).flush({});
  });

  it('DELETE /course-categories/:id', () => {
    service.deleteCourseCategory('c1').subscribe();
    const req = httpMock.expectOne(`${base}/course-categories/c1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POST /course-categories', () => {
    service.createCourseCategory({ name: 'Core' } as unknown as CreateCourseCategoryRequest).subscribe();
    const req = httpMock.expectOne(`${base}/course-categories`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
