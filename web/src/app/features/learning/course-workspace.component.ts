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
import { AssignmentResponse, AssignmentStatus } from '../../core/models/grading.model';
import { AcademicStructureService } from '../../core/services/academic-structure.service';
import { AuthService } from '../../core/services/auth.service';
import { CourseContentService } from '../../core/services/course-content.service';
import { GradingService } from '../../core/services/grading.service';
import { LearningService } from '../../core/services/learning.service';
import { RowMenuComponent } from '../../shared/row-menu.component';
import { canPreview, errorMessage, fileIcon, openBlob, saveBlob, Toaster } from '../../shared/ui';
import { AssignmentDetailComponent } from '../grading/assignment-detail.component';
import { AssignmentFormDialogComponent } from '../grading/assignment-form-dialog.component';

/** One week, with its assignments merged in by weekId — the content and assignment
 * APIs are separate services, so this is a frontend-only join (see
 * docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7.1). */
interface WeekView extends WeekResponse {
  assignments: AssignmentResponse[];
  monthLabel: string;
  isCurrent: boolean;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function todayIso(): string {
  return toLocalInput(new Date()).slice(0, 10);
}

/**
 * One course offering's delivery: week-by-week lectures, materials and assignments,
 * grouped month-wise — the single place a course is taught from and studied in.
 * Managers (owning lecturer, admin, scoped Program/Department Head) build it here:
 * weeks, lectures, materials, and assignments (in place, via the shared assignment
 * dialog). Students read it, open materials, and open/submit assignments without
 * leaving the page. See docs/LEARNING_HUB_COURSE_CONTENT_ARCHITECTURE.md section 7.2.
 */
@Component({
  selector: 'app-course-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RowMenuComponent, AssignmentFormDialogComponent, AssignmentDetailComponent],
  templateUrl: './course-workspace.component.html',
})
export class CourseWorkspaceComponent {
  private route = inject(ActivatedRoute);
  private academic = inject(AcademicStructureService);
  private learning = inject(LearningService);
  private content = inject(CourseContentService);
  private grading = inject(GradingService);
  readonly auth = inject(AuthService);

  readonly fileIcon = fileIcon;
  readonly canPreview = canPreview;

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
  readonly isStaff = computed(() => !this.isStudent());

  readonly weekViews = computed<WeekView[]>(() => {
    const byWeek = new Map<string, AssignmentResponse[]>();
    for (const a of this.assignments()) {
      if (!a.weekId) continue;
      (byWeek.get(a.weekId) ?? byWeek.set(a.weekId, []).get(a.weekId)!).push(a);
    }
    const today = todayIso();
    return this.weeks().map((w) => ({
      ...w,
      assignments: (byWeek.get(w.id) ?? []).sort((a, b) => a.dueDate.localeCompare(b.dueDate)),
      monthLabel: MONTHS[new Date(w.startDate).getMonth()] + ' ' + new Date(w.startDate).getFullYear(),
      isCurrent: w.startDate <= today && today <= w.endDate,
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

  /** Assignments with no week, or whose week this viewer can't see (a student doesn't
   * get draft weeks) — without this an open assignment in a draft week would vanish
   * from the student's view of the course entirely. */
  readonly otherAssignments = computed(() => {
    const visible = new Set(this.weeks().map((w) => w.id));
    return this.assignments()
      .filter((a) => !a.weekId || !visible.has(a.weekId))
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  });

  readonly stats = computed(() => {
    const weeks = this.weeks();
    return {
      weeks: weeks.length,
      lectures: weeks.reduce((n, w) => n + w.sessions.length, 0),
      materials: weeks.reduce((n, w) => n + w.items.length, 0),
      assignments: this.assignments().length,
      drafts: weeks.filter((w) => w.status === 'DRAFT').length,
    };
  });

  readonly nextLecture = computed(() => {
    const now = Date.now();
    return this.weeks()
      .flatMap((w) => w.sessions)
      .filter((s) => new Date(s.endsAt).getTime() > now && s.status !== 'CANCELLED')
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt))[0] ?? null;
  });

  // --- "Add week" form ---
  readonly addingWeek = signal(false);
  weekForm = { weekNumber: 1, startDate: '', endDate: '', title: '' };

  // --- "Add lecture" / "Add material" forms, scoped to one week at a time ---
  readonly addingSessionForWeek = signal<string | null>(null);
  sessionForm = { title: '', description: '', startsAt: '', endsAt: '', deliveryMode: 'ONLINE' as DeliveryMode, meetingUrl: '', location: '' };
  readonly addingItemForWeek = signal<string | null>(null);
  itemForm: { type: ContentItemType; title: string; description: string; mode: 'file' | 'link'; externalUrl: string; file: File | null } = {
    type: 'NOTE',
    title: '',
    description: '',
    mode: 'file',
    externalUrl: '',
    file: null,
  };
  readonly saving = signal(false);

  // --- assignment dialogs ---
  /** Staff: create (with a preset week) or edit an assignment in place. */
  readonly assignmentDialog = signal<{ assignment: AssignmentResponse | null; weekId: string | null } | null>(null);
  /** Student: open one assignment to read, open the brief, and submit. */
  readonly openAssignmentId = signal<string | null>(null);
  readonly openAssignment = computed(() => this.assignments().find((a) => a.id === this.openAssignmentId()) ?? null);

  constructor() {
    this.academic.getCourseOffering(this.offeringId).subscribe({
      next: (o) => this.offering.set(o),
      error: () => {},
    });
    if (!this.isStudent()) {
      this.learning.access(this.offeringId).subscribe({ next: (a) => this.canManage.set(a.manage), error: () => {} });
    }
    this.load();
  }

  load(): void {
    this.loadError.set(null);
    this.content.listWeeks(this.offeringId).subscribe({
      next: (weeks) => {
        this.weeks.set([...weeks].sort((a, b) => a.weekNumber - b.weekNumber));
        if (weeks.length && this.expandedWeeks().size === 0) {
          // The current week (or, if none, the most recent one) opens by default.
          const today = todayIso();
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
    this.loadAssignments();
  }

  loadAssignments(): void {
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

  expandAll(open: boolean): void {
    this.expandedWeeks.set(open ? new Set(this.weeks().map((w) => w.id)) : new Set());
  }

  // ---------- weeks ----------

  openAddWeek(): void {
    const last = this.weeks().at(-1);
    const next = (last?.weekNumber ?? 0) + 1;
    let start = '';
    let end = '';
    if (last) {
      const s = new Date(last.endDate);
      s.setDate(s.getDate() + 1);
      const e = new Date(s);
      e.setDate(e.getDate() + 6);
      start = s.toISOString().slice(0, 10);
      end = e.toISOString().slice(0, 10);
    }
    this.weekForm = { weekNumber: next, startDate: start, endDate: end, title: '' };
    this.addingWeek.set(true);
  }

  submitWeek(): void {
    if (!this.weekForm.startDate || !this.weekForm.endDate) {
      this.toaster.show('Pick the week’s start and end dates.', true);
      return;
    }
    this.saving.set(true);
    this.content
      .createWeek(this.offeringId, {
        weekNumber: Number(this.weekForm.weekNumber),
        startDate: this.weekForm.startDate,
        endDate: this.weekForm.endDate,
        title: this.weekForm.title || null,
      })
      .subscribe({
        next: (w) => {
          this.saving.set(false);
          this.addingWeek.set(false);
          this.expandedWeeks.update((s) => new Set(s).add(w.id));
          this.toaster.show(`Week ${this.weekForm.weekNumber} added as a draft.`);
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.toaster.show(errorMessage(err, 'Could not add this week.'), true);
        },
      });
  }

  publishWeek(w: WeekResponse): void {
    const publishing = w.status === 'DRAFT';
    this.content
      .updateWeek(w.id, { startDate: w.startDate, endDate: w.endDate, title: w.title, status: publishing ? 'PUBLISHED' : 'DRAFT' })
      .subscribe({
        next: () => {
          this.toaster.show(publishing ? `Week ${w.weekNumber} is now visible to students.` : `Week ${w.weekNumber} hidden from students.`);
          this.load();
        },
        error: (err) => this.toaster.show(errorMessage(err, 'Could not update this week.'), true),
      });
  }

  deleteWeek(w: WeekResponse): void {
    if (!confirm(`Delete Week ${w.weekNumber}? Its lectures and materials go with it. Assignments stay, as unscheduled.`)) return;
    this.content.deleteWeek(w.id).subscribe({
      next: () => {
        this.toaster.show('Week deleted.');
        this.load();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not delete this week.'), true),
    });
  }

  // ---------- lectures ----------

  openAddSession(w: WeekResponse): void {
    this.addingItemForWeek.set(null);
    const start = new Date(`${w.startDate}T09:00`);
    this.sessionForm = {
      title: '',
      description: '',
      startsAt: toLocalInput(start),
      endsAt: toLocalInput(new Date(start.getTime() + 2 * 3600000)),
      deliveryMode: 'ONLINE',
      meetingUrl: '',
      location: '',
    };
    this.addingSessionForWeek.set(w.id);
  }

  submitSession(): void {
    const weekId = this.addingSessionForWeek();
    if (!weekId) return;
    if (!this.sessionForm.title.trim()) {
      this.toaster.show('Give the lecture a title.', true);
      return;
    }
    this.saving.set(true);
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
          this.saving.set(false);
          this.addingSessionForWeek.set(null);
          this.toaster.show('Lecture added as a draft — publish it to notify students.');
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.toaster.show(errorMessage(err, 'Could not add this lecture.'), true);
        },
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

  isLive(s: SessionResponse): boolean {
    const now = Date.now();
    return new Date(s.startsAt).getTime() <= now && now <= new Date(s.endsAt).getTime();
  }

  // ---------- materials ----------

  openAddItem(weekId: string): void {
    this.addingSessionForWeek.set(null);
    this.itemForm = { type: 'NOTE', title: '', description: '', mode: 'file', externalUrl: '', file: null };
    this.addingItemForWeek.set(weekId);
  }

  onItemFileChange(event: Event): void {
    const f = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.itemForm.file = f;
    if (f && !this.itemForm.title.trim()) this.itemForm.title = f.name.replace(/\.[^.]+$/, '');
  }

  submitItem(): void {
    const weekId = this.addingItemForWeek();
    if (!weekId) return;
    if (!this.itemForm.title.trim()) {
      this.toaster.show('Give the material a title.', true);
      return;
    }
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
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.addingItemForWeek.set(null);
        this.toaster.show('Material added as a draft — publish it to share with students.');
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.toaster.show(errorMessage(err, 'Could not add this material.'), true);
      },
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

  viewItem(item: ItemResponse, mode: 'view' | 'download'): void {
    this.content.downloadItem(item.id).subscribe({
      next: (blob) => {
        const name = item.fileName ?? item.title;
        if (mode === 'view') openBlob(blob, name);
        else saveBlob(blob, name);
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not open this file.'), true),
    });
  }

  itemIcon(item: ItemResponse): string {
    if (item.externalUrl) return item.type === 'VIDEO' ? '🎬' : '🔗';
    return fileIcon(item.fileName);
  }

  // ---------- assignments ----------

  addAssignment(weekId: string | null): void {
    this.assignmentDialog.set({ assignment: null, weekId });
  }

  editAssignment(a: AssignmentResponse): void {
    this.assignmentDialog.set({ assignment: a, weekId: a.weekId });
  }

  onAssignmentSaved(saved: AssignmentResponse): void {
    const created = !this.assignmentDialog()?.assignment;
    this.assignmentDialog.set(null);
    this.toaster.show(created ? `"${saved.title}" created as a draft — publish it when it's ready.` : `"${saved.title}" updated.`);
    this.loadAssignments();
  }

  setAssignmentStatus(a: AssignmentResponse, status: AssignmentStatus): void {
    this.grading.setStatus(a.id, status).subscribe({
      next: () => {
        const verb = status === 'OPEN' ? (a.status === 'DRAFT' ? 'published — students notified' : 'reopened') : 'closed';
        this.toaster.show(`"${a.title}" ${verb}.`);
        this.loadAssignments();
      },
      error: (err) => this.toaster.show(errorMessage(err, 'Could not change the status.'), true),
    });
  }

  studentStatus(a: AssignmentResponse): { label: string; cls: string } {
    if (a.mySubmissionStatus === 'GRADED') {
      return { label: a.myMarks !== null ? `Graded · ${a.myMarks}/${a.maxMarks}` : 'Graded', cls: 'b-success' };
    }
    if (a.mySubmissionStatus === 'SUBMITTED') return { label: 'Submitted', cls: 'b-accent' };
    if (new Date(a.dueDate).getTime() < Date.now()) return { label: 'Missed', cls: 'b-danger' };
    return { label: 'To do', cls: 'b-gold' };
  }

  courseLabel(): string {
    const o = this.offering();
    return o ? `${o.code} — ${o.name}` : '';
  }
}
