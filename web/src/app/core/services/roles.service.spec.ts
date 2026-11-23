import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RolesService } from './roles.service';

describe('RolesService', () => {
  const base = '/api/identity';
  let service: RolesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RolesService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RolesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GET /roles', () => {
    service.roles().subscribe();
    const req = httpMock.expectOne(`${base}/roles`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GET /permissions', () => {
    service.permissions().subscribe();
    const req = httpMock.expectOne(`${base}/permissions`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POST /roles with name + kind', () => {
    service.createRole('Program Head', 'LECTURER').subscribe();
    const req = httpMock.expectOne(`${base}/roles`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Program Head', kind: 'LECTURER' });
    req.flush({});
  });

  it('PATCH /roles/:id/permissions toggles a grant', () => {
    service.setGrant('r1', 'p9', true).subscribe();
    const req = httpMock.expectOne(`${base}/roles/r1/permissions`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ permissionId: 'p9', granted: true });
    req.flush({});
  });

  it('GET /users/:id/assignments', () => {
    service.assignments('u1').subscribe();
    const req = httpMock.expectOne(`${base}/users/u1/assignments`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POST /users/:id/assignments', () => {
    service.assign('u1', 'r2', 'DEPARTMENT', 'd3').subscribe();
    const req = httpMock.expectOne(`${base}/users/u1/assignments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ roleId: 'r2', scopeType: 'DEPARTMENT', scopeId: 'd3' });
    req.flush({});
  });

  it('DELETE /assignments/:id', () => {
    service.revoke('a7').subscribe();
    const req = httpMock.expectOne(`${base}/assignments/a7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('GET /users/me/permissions', () => {
    service.myPermissions().subscribe();
    const req = httpMock.expectOne(`${base}/users/me/permissions`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('scopeHolders short-circuits to [] with no HTTP when scopeIds is empty', () => {
    let result: unknown;
    service.scopeHolders('r1', 'DEPARTMENT', []).subscribe((r) => (result = r));
    expect(result).toEqual([]);
    httpMock.expectNone(() => true);
  });

  it('GET /assignments/scope-holders appends each scopeId', () => {
    service.scopeHolders('r1', 'DEPARTMENT', ['d1', 'd2']).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/assignments/scope-holders`);
    expect(req.request.params.get('roleId')).toBe('r1');
    expect(req.request.params.get('scopeType')).toBe('DEPARTMENT');
    expect(req.request.params.getAll('scopeIds')).toEqual(['d1', 'd2']);
    req.flush([]);
  });

  it('PUT /assignments/holder (null userId clears)', () => {
    service.setScopeHolder('r1', 'DEPARTMENT', 'd1', null).subscribe();
    const req = httpMock.expectOne(`${base}/assignments/holder`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roleId: 'r1', scopeType: 'DEPARTMENT', scopeId: 'd1', userId: null });
    req.flush(null);
  });
});
