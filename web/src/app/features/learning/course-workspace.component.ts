import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CourseOfferingResponse } from '../../core/models/academic-structure.model';
import {
  ContentItemType,
  DeliveryMode,
  ItemResponse,
  SessionResponse,
  WeekResponse,
} from '../../core/models/course-content.model';
import { AssignmentResponse } from '../../core/models/grading.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseContentService } from '../../core/services/course-content.service';
import { GradingService } from '../../core/services/grading.service';
import { LearningService } from '../../core/services/learning.service';
import { errorMessage, saveBlob, Toaster } from '../../shared/ui';

/** One week, with its assignments merged in by weekId — the content and assignment
 * APIs are separate services, so this is a frontend-only join (see
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7.1). */
interface WeekView extends WeekResponse {
  assignments: AssignmentResponse[];
  monthLabel: string;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * One course offering's delivery: week-by-week lecture sessions, materials and
 * assignments, grouped month-wise. Read-only for everyone who can see it; a manager
 * (the owning lecturer, an admin, or a scoped Program/Department Head) can add, edit,
 * publish and delete weeks/sessions/materials here — see
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7.2.
 */
@Component({
  selector: 'app-course-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './course-workspace.component.html',
})
export class CourseWorkspaceComponent {
  private route = inject(ActivatedRoute);
  private academic = inject(AcademicStructureService);
  private learning = inject(LearningService);
  private content = inject(CourseContentService);
  private grading = inject(GradingService);
  readonly auth = inject(AuthService);

  readonly offeringId = this.route.snapshot.paramMap.get('offeringId') ?? '';
  readonly offering = signal<CourseOfferingResponse | null>(null);
  readonly weeks = signal<WeekResponse[]>([]);
  readonly assignments = signal<AssignmentResponse[]>([]);
  readonly canManage = signal(false);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly expandedWeeks = signal<Set<string>>(new Set());
  readonly toast = signal<{ message: string; error: boolean } | null>(null);
  private toaster = new Toaster((t) => this.toast.set(t));

  readonly isStudent = computed(() => this.auth.currentUser()?.role === 'STUDENT');

  readonly weekViews = computed<WeekView[]>(() => {
    const byWeek = new Map<string, AssignmentResponse[]>();
    for (const a of this.assignments()) {
      if (!a.weekId) continue;
      (byWeek.get(a.weekId) ?? byWeek.set(a.weekId, []).get(a.weekId)!).push(a);
    }
    return this.weeks().map((w) => ({
      ...w,
      assignments: byWeek.get(w.id) ?? [],
      monthLabel: MONTHS[new Date(w.startDate).getMonth()] + ' ' + new Date(w.startDate).getFullYear(),
    }));
  });

  readonly groupedByMonth = computed(() => {
    const groups: { month: string; weeks: WeekView[] }[] = [];
    for (const w of this.weekViews()) {
      const last = groups.at(-1);
      if (last && last.month === w.monthLabel) last.weeks.push(w);
      else groups.push({ month: w.monthLabel, weeks: [w] });
    }
    return groups;
  });

  readonly unscheduledAssignments = computed(() => this.assignments().filter((a) => !a.weekId));

  // --- "Add week" form ---
  readonly addingWeek = signal(false);
  weekForm = { weekNumber: 1, startDate: '', endDate: '', title: '' };

  // --- "Add session" / "Add material" forms, scoped to one week at a time ---
  readonly addingSessionForWeek = signal<string | null>(null);
  sessionForm = { title: '', description: '', startsAt: '', endsAt: '', deliveryMode: 'ONLINE' as DeliveryMode, meetingUrl: '', location: '' };
  readonly addingItemForWeek = signal<string | null>(null);
  itemForm: { type: ContentItemType; title: string; description: string; mode: 'file' | 'link'; externalUrl: string; file: File | null } = {
    type: 'NOTE',
    title: '',
    description: '',
    mode: 'link',
    externalUrl: '',
    file: null,
  };

  constructor() {
    this.academic.getCourseOffering(this.offeringId).subscribe({
      next: (o) => this.offering.set(o),
      error: () => {},
    });
    const accessCheck = this.isStudent() ? null : this.learning.access(this.offeringId);
    if (accessCheck) {
      accessCheck.subscribe({ next: (a) => this.canManage.set(a.manage), error: () => {} });
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.content.listWeeks(this.offeringId).subscribe({
      next: (weeks) => {
        this.weeks.set([...weeks].sort((a, b) => a.weekNumber - b.weekNumber));
        if (weeks.length && this.expandedWeeks().size === 0) {
          // The soonest-upcoming (or, if none, the most recent) week opens by default.
          const today = new Date().toISOString().slice(0, 10);
          const current = weeks.find((w) => w.startDate <= today && today <= w.endDate) ?? weeks.at(-1);
          if (current) this.expandedWeeks.set(new Set([current.id]));
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(errorMessage(err, 'Could not load this course.'));
        this.loading.set(false);
      },
    });
    this.grading.list({ offeringId: this.offeringId, size: 100 }).subscribe({
      next: (p) => this.assignments.set(p.content),
      error: () => {},
    });
  }

  isExpanded(weekId: string): boolean {
    return this.expandedWeeks().has(weekId);
  }

  toggleWeek(weekId: string): void {
    this.expandedWeeks.update((set) => {
      const next = new Set(set);
      if (next.has(weekId)) next.delete(weekId);
      else next.add(weekId);
      return next;
    });
  }

  openAddWeek(): void {
    const next = (this.weeks().at(-1)?.weekNumber ?? 0) + 1;
    this.weekForm = { weekNumber: next, startDate: '', endDate: '', title: '' };
    this.addingWeek.set(true);
  }

  submitWeek(): void {
    if (!this.weekForm.startDate || !this.weekForm.endDate) return;
    this.content
      .createWeek(this.offeringId, {
        weekNumber: Number(this.weekForm.weekNumber),
        startDate: this.weekForm.startDate,
        endDate: this.weekForm.endDate,
        title: this.weekForm.title || null,
      })
      .subscribe({
        next: () => {
          this.addingWeek.set(false);
          this.toaster.show(`Week ${this.weekForm.weekNumber} added.`);
          this.load();
        },
        error: (err) => this.toaster.show(errorMessage(err, 'Could not add this week.'), true),
      });
  }

  publishWeek(w: WeekResponse): void {
    this.content
      .updateWeek(w.id, { startDate: w.startDate, endDate: w.endDate, title: w.title, status: w.status === 'DRAFT' ? 'PUBLISHED' : 'DRAFT' })
      .subscribe({
        next: () => {
          this.toaster.show(w.status === 'DRAFT' ? 'Week published.' : 'Week moved back to draft.');
          this.load();
        },
        error: (err) => this.toaster.show(errorMessage(err, 'Could not update this week.'), true),
      });
  }

  deleteWeek(w: WeekResponse): void {
    if (!confirm(`Delete Week ${w.weekNumber}? Its lectures and materials go with it.`)) return;
    this.content.deleteWeek(w.id).subscribe({
      next: () => {
        this.toaster.show('Week deleted.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this week.'), true),
    });
  }

  openAddSession(weekId: string): void {
    const now = new Date();
    this.sessionForm = {
      title: '',
      description: '',
      startsAt: toLocalInput(now),
      endsAt: toLocalInput(new Date(now.getTime() + 3600000)),
      deliveryMode: 'ONLINE',
      meetingUrl: '',
      location: '',
    };
    this.addingSessionForWeek.set(weekId);
  }

  submitSession(): void {
    const weekId = this.addingSessionForWeek();
    if (!weekId || !this.sessionForm.title.trim()) return;
    this.content
      .createSession(weekId, {
        title: this.sessionForm.title.trim(),
        description: this.sessionForm.description || null,
        startsAt: new Date(this.sessionForm.startsAt).toISOString(),
        endsAt: new Date(this.sessionForm.endsAt).toISOString(),
        deliveryMode: this.sessionForm.deliveryMode,
        meetingUrl: this.sessionForm.meetingUrl || null,
        location: this.sessionForm.location || null,
      })
      .subscribe({
        next: () => {
          this.addingSessionForWeek.set(null);
          this.toaster.show('Lecture added.');
          this.load();
        },
        error: (err) => this.toaster.show(errorMessage(err, 'Could not add this lecture.'), true),
      });
  }

  publishSession(s: SessionResponse): void {
    this.content
      .updateSession(s.id, { ...s, status: s.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED' })
      .subscribe({
        next: () => {
          this.toaster.show(s.status === 'PUBLISHED' ? 'Lecture moved back to draft.' : 'Lecture published — students notified.');
          this.load();
        },
        error: (err) => this.toaster.show(errorMessage(err, 'Could not update this lecture.'), true),
      });
  }

  setRecordingUrl(s: SessionResponse): void {
    const url = prompt('Recording link (https://…)', s.recordingUrl ?? '');
    if (url === null) return;
    this.content.updateSession(s.id, { ...s, recordingUrl: url || null }).subscribe({
      next: () => {
        this.toaster.show('Recording link saved.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not save the recording link.'), true),
    });
  }

  deleteSession(s: SessionResponse): void {
    if (!confirm(`Delete "${s.title}"?`)) return;
    this.content.deleteSession(s.id).subscribe({
      next: () => {
        this.toaster.show('Lecture deleted.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this lecture.'), true),
    });
  }

  openAddItem(weekId: string): void {
    this.itemForm = { type: 'NOTE', title: '', description: '', mode: 'link', externalUrl: '', file: null };
    this.addingItemForWeek.set(weekId);
  }

  onItemFileChange(event: Event): void {
    this.itemForm.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  submitItem(): void {
    const weekId = this.addingItemForWeek();
    if (!weekId || !this.itemForm.title.trim()) return;
    const call =
      this.itemForm.mode === 'link'
        ? this.content.createLinkItem({
            weekId,
            type: this.itemForm.type,
            title: this.itemForm.title.trim(),
            description: this.itemForm.description || null,
            externalUrl: this.itemForm.externalUrl.trim(),
          })
        : this.itemForm.file
          ? this.content.uploadFileItem(weekId, null, this.itemForm.type, this.itemForm.title.trim(), this.itemForm.description || null, this.itemForm.file)
          : null;
    if (!call) {
      this.toaster.show('Choose a file to upload.', true);
      return;
    }
    call.subscribe({
      next: () => {
        this.addingItemForWeek.set(null);
        this.toaster.show('Material added.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not add this material.'), true),
    });
  }

  publishItem(item: ItemResponse): void {
    this.content.setItemStatus(item.id, item.status === 'DRAFT' ? 'PUBLISHED' : 'DRAFT').subscribe({
      next: () => {
        this.toaster.show(item.status === 'DRAFT' ? 'Material published — students notified.' : 'Material moved back to draft.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not update this material.'), true),
    });
  }

  deleteItem(item: ItemResponse): void {
    if (!confirm(`Delete "${item.title}"?`)) return;
    this.content.deleteItem(item.id).subscribe({
      next: () => {
        this.toaster.show('Material deleted.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this material.'), true),
    });
  }

  downloadItem(item: ItemResponse): void {
    this.content.downloadItem(item.id).subscribe({
      next: (blob) => saveBlob(blob, item.fileName ?? item.title),
      error: (err) => this.toaster.show(errorMessage(err, 'Could not download this file.'), true),
    });
  }
}
