import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RowMenuComponent } from './row-menu.component';

/** Host that projects a real action button, so projection + close-on-choose can be tested. */
@Component({
  standalone: true,
  imports: [RowMenuComponent],
  template: `
    <app-row-menu label="Row actions">
      <button class="row-menu-item" (click)="chosen = true">Edit</button>
      <button class="row-menu-item" disabled>Disabled</button>
    </app-row-menu>
  `,
})
class HostComponent {
  chosen = false;
}

describe('RowMenuComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  function menu(): RowMenuComponent {
    return fixture.debugElement.children[0].componentInstance as RowMenuComponent;
  }
  function trigger(): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.row-menu-trigger')!;
  }
  function panel(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.row-menu-panel');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('is closed initially with no panel rendered', () => {
    expect(menu().open()).toBeFalse();
    expect(panel()).toBeNull();
    expect(trigger().getAttribute('aria-label')).toBe('Row actions');
  });

  it('toggle opens the panel, and toggling again closes it', () => {
    trigger().click();
    fixture.detectChanges();
    expect(menu().open()).toBeTrue();
    expect(panel()).not.toBeNull();
    expect(trigger().getAttribute('aria-expanded')).toBe('true');

    trigger().click();
    fixture.detectChanges();
    expect(menu().open()).toBeFalse();
    expect(panel()).toBeNull();
  });

  it('closes after an enabled action is chosen', fakeAsync(() => {
    trigger().click();
    fixture.detectChanges();
    tick(); // the trigger's deferred focus()

    const editItem = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.row-menu-item')!;
    editItem.click();
    fixture.detectChanges();

    expect(host.chosen).toBeTrue();
    expect(menu().open()).toBeFalse();
  }));

  it('Escape closes the menu', () => {
    trigger().click();
    fixture.detectChanges();
    expect(menu().open()).toBeTrue();

    menu().onEscape();
    fixture.detectChanges();
    expect(menu().open()).toBeFalse();
  });

  it('a pointerdown outside the host closes the menu', () => {
    trigger().click();
    fixture.detectChanges();
    expect(menu().open()).toBeTrue();

    document.dispatchEvent(new PointerEvent('pointerdown'));
    fixture.detectChanges();
    expect(menu().open()).toBeFalse();
  });
});
