import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationResponse } from '../../core/models/notification.model';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnDestroy {
  private host = inject(ElementRef<HTMLElement>);
  readonly notifications = inject(NotificationService);
  readonly userMenuOpen = signal(false);
  readonly bellOpen = signal(false);
  readonly liveToast = signal<NotificationResponse | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    readonly auth: AuthService,
    private router: Router,
  ) {
    if (this.auth.isAuthenticated()) this.notifications.connect(() => this.auth.freshToken());
    // Show each live-pushed notification briefly.
    effect(() => {
      const n = this.notifications.incoming();
      if (!n) return;
      this.liveToast.set(n);
      if (this.toastTimer) clearTimeout(this.toastTimer);
      this.toastTimer = setTimeout(() => this.liveToast.set(null), 5000);
    });
  }

  ngOnDestroy(): void {
    this.notifications.disconnect();
  }

  initialsOf(name: string): string {
    return name
      .split(' ')
      .map((part) => part[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  toggleUserMenu(): void {
    this.userMenuOpen.update((open) => !open);
  }

  toggleBell(): void {
    this.bellOpen.update((open) => !open);
    if (this.bellOpen()) this.notifications.refresh();
  }

  open(n: NotificationResponse): void {
    this.bellOpen.set(false);
    this.liveToast.set(null);
    if (!n.read) this.notifications.markRead(n.id).subscribe({ error: () => undefined });
    if (n.link) this.router.navigateByUrl(n.link);
  }

  markAllRead(): void {
    this.notifications.markAllRead().subscribe({ error: () => undefined });
  }

  /** Ends the session at Keycloak too, which then sends the browser to /login. */
  logout(): void {
    this.notifications.disconnect();
    this.auth.logout();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as Node;
    if (this.userMenuOpen()) {
      const wrap = this.host.nativeElement.querySelector('.user-menu-wrap');
      if (wrap && !wrap.contains(target)) this.userMenuOpen.set(false);
    }
    if (this.bellOpen()) {
      const bell = this.host.nativeElement.querySelector('.bell-wrap');
      if (bell && !bell.contains(target)) this.bellOpen.set(false);
    }
  }
}
