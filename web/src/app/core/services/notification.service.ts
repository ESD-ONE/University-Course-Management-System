import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { NOTIFICATION_API_BASE_URL } from '../config/api.config';
import { NotificationPage, NotificationPreferences, NotificationResponse } from '../models/notification.model';

/**
 * In-app notifications plus the live push stream. The stream is Server-Sent Events read
 * through fetch() rather than EventSource, because EventSource can't send an
 * Authorization header — this way the Gateway's JWT and logout-revocation checks apply to
 * the stream exactly like any other request. Reconnects with backoff if it drops, asking
 * for a fresh token each time — access tokens only live a few minutes.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly unreadCount = signal(0);
  readonly recent = signal<NotificationResponse[]>([]);
  /** The most recent live-pushed notification, for a transient toast. */
  readonly incoming = signal<NotificationResponse | null>(null);

  private abort: AbortController | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelayMs = 2000;

  constructor(private http: HttpClient) {}

  list(page = 0, size = 20, unreadOnly = false): Observable<NotificationPage> {
    const params = new HttpParams().set('page', page).set('size', size).set('unreadOnly', unreadOnly);
    return this.http
      .get<NotificationPage>(`${NOTIFICATION_API_BASE_URL}/notifications/me`, { params })
      .pipe(tap((p) => this.unreadCount.set(p.unreadCount)));
  }

  refresh(): void {
    this.list(0, 8).subscribe({
      next: (p) => this.recent.set(p.items),
      error: () => undefined,
    });
  }

  markRead(id: string): Observable<NotificationResponse> {
    return this.http.patch<NotificationResponse>(`${NOTIFICATION_API_BASE_URL}/notifications/${id}/read`, {}).pipe(
      tap((n) => {
        this.recent.update((items) => items.map((i) => (i.id === n.id ? n : i)));
        this.unreadCount.update((c) => Math.max(0, c - 1));
      }),
    );
  }

  markAllRead(): Observable<unknown> {
    return this.http.post(`${NOTIFICATION_API_BASE_URL}/notifications/me/read-all`, {}).pipe(
      tap(() => {
        this.unreadCount.set(0);
        this.recent.update((items) => items.map((i) => ({ ...i, read: true })));
      }),
    );
  }

  preferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${NOTIFICATION_API_BASE_URL}/preferences/me`);
  }

  updatePreferences(prefs: NotificationPreferences): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(`${NOTIFICATION_API_BASE_URL}/preferences/me`, prefs);
  }

  /** Opens the live stream (idempotent). {@code token} is asked again on every reconnect. */
  connect(token: () => Promise<string | null>): void {
    if (this.abort) return;
    this.refresh();
    this.abort = new AbortController();
    this.read(token, this.abort.signal);
  }

  disconnect(): void {
    this.abort?.abort();
    this.abort = null;
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.unreadCount.set(0);
    this.recent.set([]);
    this.incoming.set(null);
  }

  private async read(token: () => Promise<string | null>, signal: AbortSignal): Promise<void> {
    try {
      const current = await token();
      if (!current) {
        this.abort = null;
        return;
      }
      const response = await fetch(`${NOTIFICATION_API_BASE_URL}/notifications/stream`, {
        headers: { Authorization: `Bearer ${current}`, Accept: 'text/event-stream' },
        signal,
      });
      if (response.status === 401 || response.status === 403) {
        this.abort = null;
        return;
      }
      if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
      this.retryDelayMs = 2000;
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary: number;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          this.handleEvent(buffer.slice(0, boundary));
          buffer = buffer.slice(boundary + 2);
        }
      }
      throw new Error('stream ended');
    } catch {
      if (signal.aborted) return;
      this.retryTimer = setTimeout(() => {
        if (!signal.aborted) this.read(token, signal);
      }, this.retryDelayMs);
      this.retryDelayMs = Math.min(this.retryDelayMs * 2, 60000);
    }
  }

  private handleEvent(raw: string): void {
    let event = 'message';
    const data: string[] = [];
    for (const line of raw.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).trim());
    }
    if (event !== 'notification' || !data.length) return;
    try {
      const n = JSON.parse(data.join('\n')) as NotificationResponse;
      this.recent.update((items) => [n, ...items.filter((i) => i.id !== n.id)].slice(0, 8));
      this.unreadCount.update((c) => c + 1);
      this.incoming.set(n);
    } catch {
      // ignore malformed frames
    }
  }
}
