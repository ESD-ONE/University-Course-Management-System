import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CanDirective } from './can.directive';
import { AuthService } from '../core/services/auth.service';

@Component({
  standalone: true,
  imports: [CanDirective],
  template: `
    <span class="plain" *can="'Courses:Edit'">plain</span>
    <span class="scoped" *can="'Courses:Edit'; scope: scopeId">scoped</span>
    <span class="malformed" *can="'Courses'">malformed</span>
  `,
})
class HostComponent {
  scopeId: string | undefined = 'program-1';
}

describe('CanDirective', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let fixture: ComponentFixture<HostComponent>;

  function shown(selector: string): boolean {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) !== null;
  }

  function create(): void {
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['can']);
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  it('renders the template when the permission is granted', () => {
    auth.can.and.returnValue(true);
    create();
    expect(shown('.plain')).toBeTrue();
    expect(auth.can).toHaveBeenCalledWith('Courses', 'Edit', undefined);
  });

  it('renders nothing when the permission is denied', () => {
    auth.can.and.returnValue(false);
    create();
    expect(shown('.plain')).toBeFalse();
    expect(shown('.scoped')).toBeFalse();
  });

  it('checks a scoped permission against the given scope only', () => {
    auth.can.and.callFake((_module, _action, scopeId) => scopeId === 'program-1');
    create();
    expect(shown('.scoped')).toBeTrue();
    expect(shown('.plain')).toBeFalse();
  });

  it('removes the template when the scope changes to one without the permission', () => {
    auth.can.and.callFake((_module, _action, scopeId) => scopeId === 'program-1');
    create();
    expect(shown('.scoped')).toBeTrue();

    fixture.componentInstance.scopeId = 'program-2';
    fixture.detectChanges();
    expect(shown('.scoped')).toBeFalse();
  });

  it('hides the template for a permission that is not "Module:Action"', () => {
    auth.can.and.returnValue(true);
    create();
    expect(shown('.malformed')).toBeFalse();
  });
});
