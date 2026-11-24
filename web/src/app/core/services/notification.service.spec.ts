import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import { NotificationPreferences, NotificationResponse } from '../models/notification.model';

describe('NotificationService', () => {
  const base = '/api/notification';
  let service: NotificationService;
  let httpMock: HttpTestingController;

  function notif(id: string, read = false): NotificationResponse {
    return { id, read } as unknown as NotificationResponse;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotificationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /notifications/me forwards paging params and syncs unreadCount', () => {
    service.list(2, 10, true).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/notifications/me`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('unreadOnly')).toBe('true');
    req.flush({ items: [], unreadCount: 7 });
    expect(service.unreadCount()).toBe(7);
  });

  it('list() defaults to page 0, size 20, unreadOnly false', () => {
    service.list().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/notifications/me`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('unreadOnly')).toBe('false');
    req.flush({ items: [], unreadCount: 0 });
  });

  it('refresh() loads the 8 most recent into the recent signal', () => {
    service.refresh();
    const req = httpMock.expectOne((r) => r.url === `${base}/notifications/me`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('8');
    req.flush({ items: [notif('n1'), notif('n2')], unreadCount: 2 });
    expect(service.recent().map((n) => n.id)).toEqual(['n1', 'n2']);
  });

  it('markRead() patches, replaces the item, and decrements unreadCount', () => {
    // seed state via a refresh
    service.refresh();
    httpMock.expectOne((r) => r.url === `${base}/notifications/me`).flush({ items: [notif('n1')], unreadCount: 3 });

    service.markRead('n1').subscribe();
    const req = httpMock.expectOne(`${base}/notifications/n1/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(notif('n1', true));

    expect(service.recent()[0].read).toBeTrue();
    expect(service.unreadCount()).toBe(2);
  });

  it('markAllRead() zeroes unreadCount and marks every recent item read', () => {
    service.refresh();
    httpMock.expectOne((r) => r.url === `${base}/notifications/me`).flush({ items: [notif('n1'), notif('n2')], unreadCount: 5 });

    service.markAllRead().subscribe();
    const req = httpMock.expectOne(`${base}/notifications/me/read-all`);
    expect(req.request.method).toBe('POST');
    req.flush({});

    expect(service.unreadCount()).toBe(0);
    expect(service.recent().every((n) => n.read)).toBeTrue();
  });

  it('GET /preferences/me', () => {
    service.preferences().subscribe();
    httpMock.expectOne(`${base}/preferences/me`).flush({});
  });

  it('PUT /preferences/me', () => {
    const prefs = { email: true } as unknown as NotificationPreferences;
    service.updatePreferences(prefs).subscribe();
    const req = httpMock.expectOne(`${base}/preferences/me`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(prefs);
    req.flush(prefs);
  });

  it('disconnect() clears the live-state signals', () => {
    service.unreadCount.set(4);
    service.recent.set([notif('n1')]);
    service.incoming.set(notif('n1'));

    service.disconnect();

    expect(service.unreadCount()).toBe(0);
    expect(service.recent()).toEqual([]);
    expect(service.incoming()).toBeNull();
  });
});
