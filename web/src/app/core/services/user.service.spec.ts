import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { ProfileResponse } from '../models/user.model';
import { PagedResponse } from '../models/pagination.model';

/**
 * Template for the HTTP data services: assert each method hits the right URL/verb/body and
 * passes query params through correctly. The other core services (enrollment, grading,
 * academic-structure, ...) should follow this same shape.
 */
describe('UserService', () => {
  const base = '/api/identity';
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /users/me', () => {
    const profile = { id: 'u1', name: 'Me' } as ProfileResponse;
    service.me().subscribe((r) => expect(r).toEqual(profile));
    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('GET');
    req.flush(profile);
  });

  it('PATCH /users/me sends the new name', () => {
    service.updateOwnName('New Name').subscribe();
    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'New Name' });
    req.flush({});
  });

  it('PATCH /users/me/password sends both passwords', () => {
    service.updateOwnPassword('old', 'new').subscribe();
    const req = httpMock.expectOne(`${base}/users/me/password`);
    expect(req.request.body).toEqual({ currentPassword: 'old', newPassword: 'new' });
    req.flush({});
  });

  it('GET /users applies pagination defaults', () => {
    service.list({}).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/users`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('role')).toBeFalse();
    req.flush({ content: [], totalElements: 0 } as unknown as PagedResponse<unknown>);
  });

  it('GET /users forwards every optional filter', () => {
    service.list({ page: 2, size: 50, role: 'STUDENT', roleId: 'r9', status: 'ACTIVE', q: 'ann' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/users`);
    const p = req.request.params;
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
    expect(p.get('role')).toBe('STUDENT');
    expect(p.get('roleId')).toBe('r9');
    expect(p.get('status')).toBe('ACTIVE');
    expect(p.get('q')).toBe('ann');
    req.flush({ content: [], totalElements: 0 } as unknown as PagedResponse<unknown>);
  });

  it('POST /users creates a user', () => {
    const body = { name: 'A', email: 'a@x.io', role: 'LECTURER' as const };
    service.create(body).subscribe();
    const req = httpMock.expectOne(`${base}/users`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('PATCH /users/:id/status', () => {
    service.updateStatus('u7', 'SUSPENDED').subscribe();
    const req = httpMock.expectOne(`${base}/users/u7/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'SUSPENDED' });
    req.flush({});
  });

  it('PUT /users/:id/role', () => {
    service.updateRole('u7', 'role-3').subscribe();
    const req = httpMock.expectOne(`${base}/users/u7/role`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roleId: 'role-3' });
    req.flush({});
  });
});
