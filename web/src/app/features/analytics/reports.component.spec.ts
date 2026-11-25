import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportsComponent } from './reports.component';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ReportResponse, ScheduleResponse } from '../../core/models/analytics.model';

describe('ReportsComponent', () => {
  let analytics: jasmine.SpyObj<AnalyticsService>;

  const report = { id: 'r1', name: 'Enrollment', format: 'PDF', lastGeneratedAt: '2025-01-01T00:00:00Z' } as ReportResponse;
  const schedule = { id: 's1', reportName: 'Enrollment', frequencyLabel: 'Weekly, Mon', status: 'ACTIVE' } as ScheduleResponse;

  function make(): ReportsComponent {
    return TestBed.createComponent(ReportsComponent).componentInstance;
  }

  beforeEach(() => {
    analytics = jasmine.createSpyObj<AnalyticsService>('AnalyticsService', [
      'reports', 'schedules', 'generate', 'download', 'createSchedule', 'setScheduleStatus', 'deleteSchedule',
    ]);
    analytics.reports.and.returnValue(of([report]));
    analytics.schedules.and.returnValue(of([schedule]));
    TestBed.configureTestingModule({ providers: [{ provide: AnalyticsService, useValue: analytics }] });
  });

  it('loads reports and schedules on construction', () => {
    const c = make();
    expect(c.reports().length).toBe(1);
    expect(c.schedules().length).toBe(1);
    expect(c.loading()).toBeFalse();
  });

  it('records a load error', () => {
    analytics.reports.and.returnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loadError()).toBeTruthy();
  });

  it('generate refreshes that report in place', () => {
    analytics.generate.and.returnValue(of({ ...report, lastGeneratedAt: '2025-02-02T00:00:00Z' }));
    const c = make();
    c.generate(report);
    expect(analytics.generate).toHaveBeenCalledWith('r1');
    expect(c.reports()[0].lastGeneratedAt).toContain('2025-02-02');
    expect(c.toast()?.message).toContain('generated');
  });

  it('download saves the returned blob', () => {
    const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    analytics.download.and.returnValue(of(new Blob()));
    const c = make();
    c.download(report);
    expect(analytics.download).toHaveBeenCalledWith('r1');
    expect(createUrl).toHaveBeenCalled();
  });

  it('openSchedule defaults the form to the first report', () => {
    const c = make();
    c.openSchedule();
    expect(c.scheduling()).toBeTrue();
    expect(c.form.controls.reportId.value).toBe('r1');
  });

  it('saveSchedule rejects an invalid form', () => {
    const c = make();
    c.openSchedule();
    c.form.patchValue({ recipients: '' }); // required
    c.saveSchedule();
    expect(analytics.createSchedule).not.toHaveBeenCalled();
  });

  it('saveSchedule posts a weekly schedule with the day of week', () => {
    analytics.createSchedule.and.returnValue(of(schedule));
    const c = make();
    c.openSchedule();
    c.form.patchValue({ recipients: 'dean@ucms.local', frequency: 'WEEKLY', dayOfWeek: 'FRIDAY' });
    c.saveSchedule();
    expect(analytics.createSchedule).toHaveBeenCalledWith(jasmine.objectContaining({ reportId: 'r1', frequency: 'WEEKLY', dayOfWeek: 'FRIDAY', dayOfMonth: null }));
  });

  it('toggle flips a schedule between active and paused', () => {
    analytics.setScheduleStatus.and.returnValue(of({ ...schedule, status: 'PAUSED' }));
    const c = make();
    c.toggle(schedule);
    expect(analytics.setScheduleStatus).toHaveBeenCalledWith('s1', 'PAUSED');
    expect(c.schedules()[0].status).toBe('PAUSED');
  });

  it('remove confirms then deletes a schedule', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    analytics.deleteSchedule.and.returnValue(of(void 0));
    const c = make();
    c.remove(schedule);
    expect(analytics.deleteSchedule).toHaveBeenCalledWith('s1');
    expect(c.schedules().length).toBe(0);
  });
});
