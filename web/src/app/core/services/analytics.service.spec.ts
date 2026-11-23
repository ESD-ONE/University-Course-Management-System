import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AnalyticsService } from './analytics.service';
import { ScheduleRequest } from '../models/analytics.model';

describe('AnalyticsService', () => {
  const base = '/api/analytics';
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /dashboards/admin', () => {
    service.adminDashboard().subscribe();
    httpMock.expectOne(`${base}/dashboards/admin`).flush({});
  });

  it('POST /dashboards/admin/refresh', () => {
    service.refreshAdminDashboard().subscribe();
    const req = httpMock.expectOne(`${base}/dashboards/admin/refresh`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('GET /dashboards/lecturer', () => {
    service.lecturerDashboard().subscribe();
    httpMock.expectOne(`${base}/dashboards/lecturer`).flush({});
  });

  it('GET /dashboards/student', () => {
    service.studentDashboard().subscribe();
    httpMock.expectOne(`${base}/dashboards/student`).flush({});
  });

  it('GET /reports', () => {
    service.reports().subscribe();
    httpMock.expectOne(`${base}/reports`).flush([]);
  });

  it('POST /reports/:id/generate', () => {
    service.generate('r1').subscribe();
    const req = httpMock.expectOne(`${base}/reports/r1/generate`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('GET /reports/:id/download requests a blob', () => {
    service.download('r1').subscribe();
    const req = httpMock.expectOne(`${base}/reports/r1/download`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('GET /scheduled-reports', () => {
    service.schedules().subscribe();
    httpMock.expectOne(`${base}/scheduled-reports`).flush([]);
  });

  it('POST /scheduled-reports', () => {
    const body = { reportType: 'ENROLLMENT' } as unknown as ScheduleRequest;
    service.createSchedule(body).subscribe();
    const req = httpMock.expectOne(`${base}/scheduled-reports`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('PATCH /scheduled-reports/:id/status', () => {
    service.setScheduleStatus('s1', 'PAUSED').subscribe();
    const req = httpMock.expectOne(`${base}/scheduled-reports/s1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'PAUSED' });
    req.flush({});
  });

  it('DELETE /scheduled-reports/:id', () => {
    service.deleteSchedule('s1').subscribe();
    const req = httpMock.expectOne(`${base}/scheduled-reports/s1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
