import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

/**
 * The interceptor is what actually attaches auth to every API call and reacts to a
 * rejected token, so it's worth pinning: token attachment, the pass-through rules, and
 * the 401 -> sign-out behaviour.
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['freshToken', 'sessionExpired']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('attaches a fresh bearer token to /api/ requests', () => {
    auth.freshToken.and.resolveTo('tok-123');
    http.get('/api/identity/users/me').subscribe();

    // flush is async because freshToken() is a promise; microtask then settles it.
    return Promise.resolve().then(() => {
      const req = httpMock.expectOne('/api/identity/users/me');
      expect(req.request.headers.get('Authorization')).toBe('Bearer tok-123');
      req.flush({});
    });
  });

  it('leaves non-/api/ requests untouched', () => {
    http.get('/assets/config.json').subscribe();
    const req = httpMock.expectOne('/assets/config.json');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    expect(auth.freshToken).not.toHaveBeenCalled();
    req.flush({});
  });

  it('does not overwrite a request that already carries Authorization', () => {
    http.get('/api/identity/auth/logout', { headers: { Authorization: 'Bearer caller' } }).subscribe();
    const req = httpMock.expectOne('/api/identity/auth/logout');
    expect(req.request.headers.get('Authorization')).toBe('Bearer caller');
    expect(auth.freshToken).not.toHaveBeenCalled();
    req.flush({});
  });

  it('sends the request without a header when there is no token', async () => {
    auth.freshToken.and.resolveTo(null);
    http.get('/api/identity/users/me').subscribe();
    await Promise.resolve();
    const req = httpMock.expectOne('/api/identity/users/me');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('signs the user out on a 401 and re-throws', async () => {
    auth.freshToken.and.resolveTo('tok-123');
    let captured: HttpErrorResponse | undefined;
    http.get('/api/identity/users/me').subscribe({
      error: (e: HttpErrorResponse) => (captured = e),
    });
    await Promise.resolve();
    const req = httpMock.expectOne('/api/identity/users/me');
    req.flush('nope', { status: 401, statusText: 'Unauthorized' });
    expect(auth.sessionExpired).toHaveBeenCalledTimes(1);
    expect(captured?.status).toBe(401);
  });

  it('does not sign the user out on a non-401 error', async () => {
    auth.freshToken.and.resolveTo('tok-123');
    http.get('/api/identity/users/me').subscribe({ error: () => undefined });
    await Promise.resolve();
    const req = httpMock.expectOne('/api/identity/users/me');
    req.flush('boom', { status: 500, statusText: 'Server Error' });
    expect(auth.sessionExpired).not.toHaveBeenCalled();
  });
});
