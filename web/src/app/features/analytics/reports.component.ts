import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ReportResponse, ScheduleResponse } from '../../core/models/analytics.model';
import { AnalyticsService } from '../../core/services/analytics.service';
import { errorMessage, saveBlob, Toaster } from '../../shared/ui';

/** Wireframe adminReports: on-demand reports (Generate stores a fresh file, Download
 *  fetches the latest) and scheduled reports emailed to recipients. */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './reports.component.html',
})
export class ReportsComponent {
  private fb = inject(FormBuilder);
  private analytics = inject(AnalyticsService);

  readonly reports = signal<ReportResponse[]>([]);
  readonly schedules = signal<ScheduleResponse[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly busyId = signal<string | null>(null);
  readonly scheduling = signal(false);
  readonly dialogError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  readonly form = this.fb.group({
    reportId: ['', Validators.required],
    frequency: ['WEEKLY' as 'DAILY' | 'WEEKLY' | 'MONTHLY', Validators.required],
    time: ['08:00', Validators.required],
    dayOfWeek: ['MONDAY'],
    dayOfMonth: [1],
    recipients: ['', Validators.required],
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.analytics.reports().subscribe({
      next: (v) => {
        this.reports.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load reports.'));
        this.loading.set(false);
      },
    });
    this.analytics.schedules().subscribe({ next: (v) => this.schedules.set(v) });
  }

  generate(r: ReportResponse): void {
    this.busyId.set(r.id);
    this.analytics.generate(r.id).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.reports.update((rs) => rs.map((x) => (x.id === updated.id ? updated : x)));
        this.toaster.show(`${r.name} generated.`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.toaster.show(errorMessage(err, `Could not generate ${r.name}.`), true);
      },
    });
  }

  download(r: ReportResponse): void {
    this.analytics.download(r.id).subscribe({
      next: (blob) => {
        const date = (r.lastGeneratedAt ?? '').slice(0, 10);
        saveBlob(blob, `${r.name.replace(/[^A-Za-z0-9]+/g, '-')}-${date}.${r.format.toLowerCase()}`);
      },
      error: (err) => this.toaster.show(errorMessage(err, `Could not download ${r.name}.`), true),
    });
  }

  openSchedule(): void {
    this.form.reset({ reportId: this.reports()[0]?.id ?? '', frequency: 'WEEKLY', time: '08:00', dayOfWeek: 'MONDAY', dayOfMonth: 1, recipients: '' });
    this.dialogError.set(null);
    this.scheduling.set(true);
  }

  saveSchedule(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.analytics
      .createSchedule({
        reportId: v.reportId ?? '',
        frequency: v.frequency ?? 'WEEKLY',
        time: v.time ?? '08:00',
        dayOfWeek: v.frequency === 'WEEKLY' ? v.dayOfWeek : null,
        dayOfMonth: v.frequency === 'MONTHLY' ? Number(v.dayOfMonth) : null,
        recipients: v.recipients ?? '',
      })
      .subscribe({
        next: (s) => {
          this.submitting.set(false);
          this.scheduling.set(false);
          this.toaster.show(`${s.reportName} scheduled — ${s.frequencyLabel}.`);
          this.load();
        },
        error: (err) => {
          this.submitting.set(false);
          this.dialogError.set(errorMessage(err, 'Could not create this schedule.'));
        },
      });
  }

  toggle(s: ScheduleResponse): void {
    const next = s.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    this.analytics.setScheduleStatus(s.id, next).subscribe({
      next: (u) => this.schedules.update((list) => list.map((x) => (x.id === u.id ? u : x))),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not update this schedule.'), true),
    });
  }

  remove(s: ScheduleResponse): void {
    if (!confirm(`Stop sending ${s.reportName} (${s.frequencyLabel})?`)) return;
    this.analytics.deleteSchedule(s.id).subscribe({
      next: () => this.schedules.update((list) => list.filter((x) => x.id !== s.id)),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this schedule.'), true),
    });
  }
}
