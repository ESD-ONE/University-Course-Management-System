import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/services/auth.service';

describe('LoginComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  const route = { snapshot: { queryParamMap: convertToParamMap({}) } };

  function create(): ComponentFixture<LoginComponent> {
    return TestBed.createComponent(LoginComponent);
  }

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    auth.login.and.resolveTo(undefined);
    route.snapshot.queryParamMap = convertToParamMap({});
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: route },
      ],
    }).compileComponents();
  });

  it('shows no notice on a normal visit', () => {
    const c = create().componentInstance;
    expect(c.notice()).toBeNull();
  });

  it('explains an expired session when ?expired is present', () => {
    route.snapshot.queryParamMap = convertToParamMap({ expired: '1' });
    const c = create().componentInstance;
    expect(c.notice()).toContain('session has expired');
  });

  it('signIn() flags redirecting and sends the user to Keycloak', () => {
    const c = create().componentInstance;
    c.signIn();
    expect(c.redirecting()).toBeTrue();
    expect(auth.login).toHaveBeenCalled();
  });

  it('recovers with a notice when the sign-in service is unavailable', async () => {
    auth.login.and.rejectWith(new Error('down'));
    const c = create().componentInstance;
    c.signIn();
    await Promise.resolve(); // let the rejected login settle
    expect(c.redirecting()).toBeFalse();
    expect(c.notice()).toContain('unavailable');
  });
});
