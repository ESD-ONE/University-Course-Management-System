import { Component, ElementRef, HostListener, Input, OnDestroy, ViewChild, inject, signal } from '@angular/core';

/**
 * The ⋮ (kebab) menu for a table row's or card's actions. Put the actions inside as
 * `<button class="row-menu-item">` (add `danger` for destructive ones); the menu closes
 * as soon as one is chosen. The panel is position:fixed, so neither a table card's
 * overflow nor a modal's scroll area can clip it, and it opens upward near the bottom
 * of the screen.
 */
@Component({
  selector: 'app-row-menu',
  standalone: true,
  template: `
    <button #trigger type="button" class="row-menu-trigger" [class.open]="open()"
            [attr.aria-label]="label" [attr.title]="label" aria-haspopup="menu" [attr.aria-expanded]="open()"
            (click)="toggle($event)">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <circle cx="12" cy="5" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="12" cy="19" r="2" />
      </svg>
    </button>
    @if (open()) {
      <div #panel class="row-menu-panel" role="menu"
           [style.top.px]="top()" [style.bottom.px]="bottom()" [style.right.px]="right()"
           (click)="onPanelClick($event)">
        <ng-content />
      </div>
    }
  `,
})
export class RowMenuComponent implements OnDestroy {
  private host = inject(ElementRef<HTMLElement>);
  @Input() label = 'More actions';
  @ViewChild('trigger') private trigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('panel') private panel?: ElementRef<HTMLElement>;

  readonly open = signal(false);
  readonly top = signal<number | null>(null);
  readonly bottom = signal<number | null>(null);
  readonly right = signal(0);

  toggle(event: Event): void {
    // Rows and cards often have their own click handler (select / expand).
    event.stopPropagation();
    if (this.open()) {
      this.close();
      return;
    }
    const rect = this.trigger!.nativeElement.getBoundingClientRect();
    this.right.set(Math.max(8, window.innerWidth - rect.right));
    if (window.innerHeight - rect.bottom < 220 && rect.top > 220) {
      this.top.set(null);
      this.bottom.set(window.innerHeight - rect.top + 6);
    } else {
      this.bottom.set(null);
      this.top.set(rect.bottom + 6);
    }
    this.open.set(true);
    // Any scroll — the page, a table card, or a modal's own scroll area — would leave the
    // fixed panel floating away from its row; scroll events don't bubble, so capture them.
    window.addEventListener('scroll', this.onAnyScroll, true);
    setTimeout(() => this.panel?.nativeElement.querySelector<HTMLElement>('.row-menu-item:not([disabled])')?.focus());
  }

  onPanelClick(event: Event): void {
    event.stopPropagation();
    const item = (event.target as HTMLElement).closest('.row-menu-item');
    if (item && !(item as HTMLButtonElement).disabled) this.close();
  }

  close(): void {
    this.open.set(false);
    window.removeEventListener('scroll', this.onAnyScroll, true);
  }

  ngOnDestroy(): void {
    window.removeEventListener('scroll', this.onAnyScroll, true);
  }

  private readonly onAnyScroll = (event: Event): void => {
    if (this.panel?.nativeElement.contains(event.target as Node)) return;
    this.close();
  };

  @HostListener('document:pointerdown', ['$event'])
  onOutside(event: PointerEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.close();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close();
      this.trigger?.nativeElement.focus();
    }
  }

  @HostListener('window:resize')
  onViewportChange(): void {
    if (this.open()) this.close();
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.open() || (event.key !== 'ArrowDown' && event.key !== 'ArrowUp')) return;
    event.preventDefault();
    const items = Array.from(this.panel?.nativeElement.querySelectorAll<HTMLElement>('.row-menu-item:not([disabled])') ?? []);
    const i = items.indexOf(document.activeElement as HTMLElement);
    const next = event.key === 'ArrowDown' ? Math.min(i + 1, items.length - 1) : Math.max(i - 1, 0);
    items[next]?.focus();
  }
}
