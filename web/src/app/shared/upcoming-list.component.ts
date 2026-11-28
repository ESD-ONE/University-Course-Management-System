import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UpcomingItem } from '../core/models/grading.model';
import { AuthService } from '../core/services/auth.service';

interface Row extends UpcomingItem {
  relative: string;
  overdue: boolean;
}

/**
 * The "what's coming up" dashboard widget — lecture sessions starting soon and
 * assignments due soon, merged and time-sorted by the backend (GradingService.upcoming,
 * GET /dashboard/upcoming). Used on all three role dashboards; see
 * docs/ASSIGNMENT_WEEK_LINKING_AND_DASHBOARD_ARCHITECTURE.md section 3.2.
 */
@Component({
  selector: 'app-upcoming-list',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (rows().length) {
      <div class="upcoming-list">
        @for (r of rows(); track r.type + r.offeringId + r.at) {
          <div class="upcoming-row">
            <span class="upcoming-icon">{{ r.type === 'LECTURE' ? '🎓' : '📄' }}</span>
            <div class="upcoming-body">
              <div class="upcoming-title">{{ r.title }}</div>
              <div class="cell-faint" style="font-size:11.5px;">
                {{ r.courseCode }}@if (r.courseName) { · {{ r.courseName }} }
              </div>
            </div>
            <div class="upcoming-when">
              <span [class.danger-text]="r.overdue">{{ r.relative }}</span>
              @if (r.type === 'LECTURE') {
                <a class="btn-link" [routerLink]="['/app/learning/offerings', r.offeringId]">Open ›</a>
              } @else if (isStudent()) {
                <a class="btn-link" routerLink="/app/my-assignments">Open ›</a>
              } @else {
                <a class="btn-link" routerLink="/app/grading" [queryParams]="{ assignment: r.assignmentId }">Grade ›</a>
              }
            </div>
          </div>
        }
      </div>
    } @else {
      <p class="cell-faint" style="margin:0;font-size:12.5px;">Nothing coming up in the next {{ days() }} days.</p>
    }
  `,
  styles: [
    `
      .upcoming-list { display: flex; flex-direction: column; gap: 10px; }
      .upcoming-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--line); }
      .upcoming-row:last-child { border-bottom: none; padding-bottom: 0; }
      .upcoming-icon { font-size: 16px; }
      .upcoming-body { flex: 1; min-width: 0; }
      .upcoming-title { font-size: 13px; font-weight: 600; }
      .upcoming-when { display: flex; align-items: center; gap: 10px; font-size: 12px; white-space: nowrap; }
      .danger-text { color: var(--danger); font-weight: 600; }
    `,
  ],
})
export class UpcomingListComponent {
  private auth = inject(AuthService);

  readonly items = input<UpcomingItem[]>([]);
  readonly days = input(7);

  readonly isStudent = computed(() => this.auth.currentUser()?.role === 'STUDENT');

  readonly rows = computed<Row[]>(() => this.items().map((i) => ({ ...i, ...relativeTime(i.at) })));
}

function relativeTime(at: string): { relative: string; overdue: boolean } {
  const diffMs = new Date(at).getTime() - Date.now();
  if (diffMs <= 0) return { relative: 'Overdue', overdue: true };
  const hours = diffMs / 3600000;
  if (hours < 1) return { relative: 'In less than an hour', overdue: false };
  if (hours < 24) return { relative: `In ${Math.round(hours)}h`, overdue: false };
  const days = Math.round(hours / 24);
  return { relative: `In ${days} day${days === 1 ? '' : 's'}`, overdue: false };
}
