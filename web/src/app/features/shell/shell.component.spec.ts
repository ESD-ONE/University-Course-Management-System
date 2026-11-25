import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { WritableSignal, signal } from '@angular/core';
import { of } from 'rxjs';
import { ShellComponent } from './shell.component';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { NotificationResponse } from '../../core/models/notification.model';

/** Methods are spies; the signals Shell reads are real signals we can drive. */
interface NotifMock {
  connect: jasmine.Spy;
  disconnect: jasmine.Spy;
  refresh: jasmine.Spy;
  markRead: jasmine.Spy;
  markAllRead: jasmine.Spy;
  incoming: WritableSignal<NotificationResponse | null>;
  unreadCount: WritableSignal<number>;
  recent: WritableSignal<NotificationResponse[]>;
}

/**
 * Shell's logic is tested on the instance (its template pulls in router + many signals);
 * these cover the menu/bell toggles, opening a notification, and sign-out wiring.
 */
describe('ShellComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let notifications: NotifMock;
  let router: Router;

  function make(): ShellComponent {
    return TestBed.createComponent(ShellComponent).componentInstance;
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'freshToken', 'logout']);
    auth.isAuthenticated.and.returnValue(false);

    notifications = {
      connect: jasmine.createSpy('connect'),
      disconnect: jasmine.createSpy('disconnect'),
      refresh: jasmine.createSpy('refresh'),
      markRead: jasmine.createSpy('markRead').and.returnValue(of({} as NotificationResponse)),
      markAllRead: jasmine.createSpy('markAllRead').and.returnValue(of({})),
      incoming: signal<NotificationResponse | null>(null),
      unreadCount: signal(0),
      recent: signal<NotificationResponse[]>([]),
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications as unknown as NotificationService },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  it('connects the live stream when already authenticated', () => {
    auth.isAuthenticated.and.returnValue(true);
    make();
    expect(notifications.connect).toHaveBeenCalled();
  });

  it('does not connect the stream when signed out', () => {
    auth.isAuthenticated.and.returnValue(false);
    make();
    expect(notifications.connect).not.toHaveBeenCalled();
  });

  it('initialsOf builds up to two uppercase initials', () => {
    const c = make();
    expect(c.initialsOf('ada lovelace')).toBe('AL');
    expect(c.initialsOf('grace brewster hopper')).toBe('GB');
  });

  it('toggleUserMenu flips the menu state', () => {
    const c = make();
    expect(c.userMenuOpen()).toBeFalse();
    c.toggleUserMenu();
    expect(c.userMenuOpen()).toBeTrue();
  });

  it('toggleBell refreshes only when opening', () => {
    const c = make();
    c.toggleBell(); // opening
    expect(c.bellOpen()).toBeTrue();
    expect(notifications.refresh).toHaveBeenCalledTimes(1);
    c.toggleBell(); // closing
    expect(c.bellOpen()).toBeFalse();
    expect(notifications.refresh).toHaveBeenCalledTimes(1);
  });

  it('open() marks an unread notification read and navigates to its link', () => {
    const c = make();
    const n = { id: 'n1', read: false, link: '/app/grades' } as NotificationResponse;
    c.open(n);
    expect(notifications.markRead).toHaveBeenCalledWith('n1');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/grades');
    expect(c.bellOpen()).toBeFalse();
  });

  it('open() does not re-mark an already-read notification', () => {
    const c = make();
    c.open({ id: 'n2', read: true, link: null } as unknown as NotificationResponse);
    expect(notifications.markRead).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('logout disconnects the stream and ends the auth session', () => {
    const c = make();
    c.logout();
    expect(notifications.disconnect).toHaveBeenCalled();
    expect(auth.logout).toHaveBeenCalled();
  });
});
