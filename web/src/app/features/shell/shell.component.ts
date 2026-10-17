import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private host = inject(ElementRef<HTMLElement>);
  readonly userMenuOpen = signal(false);

  constructor(
    readonly auth: AuthService,
    private router: Router,
  ) {}

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

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.userMenuOpen()) return;
    const wrap = this.host.nativeElement.querySelector('.user-menu-wrap');
    if (wrap && !wrap.contains(event.target as Node)) {
      this.userMenuOpen.set(false);
    }
  }
}
