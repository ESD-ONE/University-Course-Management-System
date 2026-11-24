import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AcademicStructureService } from './academic-structure.service';
import {
  CreateAcademicYearRequest,
  CreateCourseRequest,
  CreateDegreeProgramRequest,
  CreateSemesterRequest,
} from '../models/academic-structure.model';

describe('AcademicStructureService', () => {
  const base = '/api/academic';
  let service: AcademicStructureService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AcademicStructureService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AcademicStructureService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /academic-years', () => {
    service.listAcademicYears().subscribe();
    httpMock.expectOne(`${base}/academic-years`).flush([]);
  });

  it('POST /academic-years', () => {
    const body = { name: '2025/2026' } as unknown as CreateAcademicYearRequest;
    service.createAcademicYear(body).subscribe();
    const req = httpMock.expectOne(`${base}/academic-years`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('DELETE /academic-years/:id', () => {
    service.deleteAcademicYear('y1').subscribe();
    const req = httpMock.expectOne(`${base}/academic-years/y1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POST /academic-years/:id/semesters (nested)', () => {
    const body = { name: 'Semester 1' } as unknown as CreateSemesterRequest;
    service.addSemester('y1', body).subscribe();
    const req = httpMock.expectOne(`${base}/academic-years/y1/semesters`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('DELETE /academic-years/:id/semesters/:sid (nested)', () => {
    service.deleteSemester('y1', 's1').subscribe();
    const req = httpMock.expectOne(`${base}/academic-years/y1/semesters/s1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('GET /degree-programs with defaults and no optional filters', () => {
    service.listDegreePrograms().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/degree-programs`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('departmentId')).toBeFalse();
    req.flush({ content: [] });
  });

  it('GET /degree-programs forwards departmentId + status + q', () => {
    service.listDegreePrograms({ departmentId: 'd1', status: 'ACTIVE', q: 'bsc' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/degree-programs`);
    expect(req.request.params.get('departmentId')).toBe('d1');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('q')).toBe('bsc');
    req.flush({ content: [] });
  });

  it('POST /degree-programs', () => {
    const body = { name: 'BSc CS' } as unknown as CreateDegreeProgramRequest;
    service.createDegreeProgram(body).subscribe();
    const req = httpMock.expectOne(`${base}/degree-programs`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('GET /degree-programs/:id/curriculum', () => {
    service.listCurriculum('p1').subscribe();
    httpMock.expectOne(`${base}/degree-programs/p1/curriculum`).flush([]);
  });

  it('GET /degree-program-academic-years omits academicYearId when absent', () => {
    service.listDegreeProgramAcademicYears().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/degree-program-academic-years`);
    expect(req.request.params.has('academicYearId')).toBeFalse();
    req.flush([]);
  });

  it('GET /degree-program-academic-years forwards academicYearId', () => {
    service.listDegreeProgramAcademicYears('y1').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/degree-program-academic-years`);
    expect(req.request.params.get('academicYearId')).toBe('y1');
    req.flush([]);
  });

  it('GET /course-offerings forwards mine + status + academicYearId', () => {
    service.listCourseOfferings({ mine: true, status: 'OPEN', academicYearId: 'y1' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/course-offerings`);
    expect(req.request.params.get('mine')).toBe('true');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('academicYearId')).toBe('y1');
    req.flush([]);
  });

  it('GET /course-offerings omits mine when false', () => {
    service.listCourseOfferings({ mine: false }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/course-offerings`);
    expect(req.request.params.has('mine')).toBeFalse();
    req.flush([]);
  });

  it('GET /courses sets mine and forwards filters', () => {
    service.listCourses(true, { degreeProgramId: 'p1', yearOfStudy: 2, status: 'PUBLISHED', q: 'algo' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/courses`);
    expect(req.request.params.get('mine')).toBe('true');
    expect(req.request.params.get('degreeProgramId')).toBe('p1');
    expect(req.request.params.get('yearOfStudy')).toBe('2');
    expect(req.request.params.get('status')).toBe('PUBLISHED');
    expect(req.request.params.get('q')).toBe('algo');
    req.flush({ content: [] });
  });

  it('POST /courses', () => {
    const body = { catalogId: 'c1' } as unknown as CreateCourseRequest;
    service.createCourse(body).subscribe();
    const req = httpMock.expectOne(`${base}/courses`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('GET /courses/:id/academic-years', () => {
    service.listCourseAcademicYears('c1').subscribe();
    httpMock.expectOne(`${base}/courses/c1/academic-years`).flush([]);
  });

  it('GET /course-catalog with defaults', () => {
    service.listCourseCatalog().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/course-catalog`);
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [] });
  });

  it('GET /course-offerings/:id', () => {
    service.getCourseOffering('o1').subscribe();
    httpMock.expectOne(`${base}/course-offerings/o1`).flush({});
  });
});
