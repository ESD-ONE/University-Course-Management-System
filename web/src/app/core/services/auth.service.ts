import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, KEYCLOAK_URL } from '../config/api.config';
import { EffectivePermission } from '../models/roles.model';
import { ProfileResponse } from '../models/user.model';
import { RolesService } from './roles.service';
import { UserService } from './user.service';

/**
 * Signing in happens on Keycloak's own login page (Authorization Code + PKCE, via
 * keycloak-js); this service starts that, keeps the resulting tokens fresh, and holds the
 * signed-in user's full profile (fetched from GET /users/me — the token itself only says
 * who they are, not their status, preferences or scope assignments, which the Users
 * screen, route guards and Profile page need).
 *
 * Tokens live in keycloak-js's memory only, never localStorage. Access tokens last five
 * minutes and are refreshed silently; a sign-in lasts at most 24 hours (the realm's SSO
 * session cap), after which refreshing fails and the user is sent back to sign in.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<ProfileResponse | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  /** True if the signed-in user holds a DEPARTMENT-scoped role_scope_assignment — e.g. a
   * "Department Head" — granting them real authority over their department's degree
   * programs/courses in academic-structure-service, beyond a plain Lecturer. */
  readonly hasDepartmentScope = computed(() =>
    (this.currentUserSignal()?.assignments ?? []).some((a) => a.scopeType === 'DEPARTMENT'),
  );

  /** Every (module, action) the signed-in user is granted, across all of their scope
   * assignments — what PermissionService-style UI checks read. The server is still the
   * real gate; this only decides what the UI shows. */
  private readonly permissionsSignal = signal<EffectivePermission[]>([]);
  readonly permissions = this.permissionsSignal.asReadonly();

  private readonly keycloak = new Keycloak({ url: KEYCLOAK_URL, realm: 'ucms', clientId: 'ucms-web' });
  private expiredNotice = false;

  constructor(
    private http: HttpClient,
    private userService: UserService,
    private rolesService: RolesService,
    private router: Router,
  ) {}

  /** True if the user is granted (module, action) somewhere — GLOBAL, or any scope at
   * all when scopeId is omitted, or specifically at scopeId when given. Admins hold a
   * GLOBAL grant on everything by default, so this naturally covers them too. */
  can(module: string, actionKey: string, scopeId?: string): boolean {
    return this.permissionsSignal().some(
      (p) =>
        p.module === module &&
        p.actionKey === actionKey &&
        (p.scopeType === 'GLOBAL' || scopeId === undefined || p.scopeId === scopeId),
    );
  }

  /**
   * Called once at app start: completes the redirect back from Keycloak's login page, or
   * silently picks up an existing Keycloak session (so a page refresh doesn't sign the
   * user out), then loads their profile before any route guard runs.
   */
  async init(): Promise<void> {
    this.keycloak.onTokenExpired = () => {
      this.keycloak.updateToken(30).catch(() => this.sessionExpired());
    };
    // The silent-check-sso iframe only works when Keycloak and the app share an origin
    // (true in production, via the nginx /auth proxy): Keycloak's own clickjacking
    // defenses put X-Frame-Options: SAMEORIGIN on its 3p-cookies probe page too, so a
    // cross-origin dev server (e.g. ng serve against the real Keycloak) gets that iframe
    // silently blocked and init() never resolves. Falls back to a full-page silent
    // redirect there instead — same effect, just visible for an instant.
    const sameOriginAsKeycloak = new URL(KEYCLOAK_URL, location.href).origin === location.origin;
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      ...(sameOriginAsKeycloak ? { silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html` } : {}),
      checkLoginIframe: false,
    });
    if (authenticated) {
      const [profile, permissions] = await Promise.all([
        firstValueFrom(this.userService.me()),
        firstValueFrom(this.rolesService.myPermissions()).catch(() => []),
      ]);
      this.currentUserSignal.set(profile);
      this.permissionsSignal.set(permissions);
    }
  }

  /** Sends the browser to Keycloak's login page; it comes back to {@code returnTo}. */
  login(returnTo = '/app'): Promise<void> {
    return this.keycloak.login({ redirectUri: `${location.origin}${returnTo}` });
  }

  getToken(): string | null {
    return this.keycloak.token ?? null;
  }

  /** A token good for at least another 30 seconds, refreshing it first if needed; null if
   *  there's no session (or it has just ended — the user is then sent to sign in). */
  async freshToken(): Promise<string | null> {
    if (!this.keycloak.authenticated) return null;
    try {
      await this.keycloak.updateToken(30);
      return this.keycloak.token ?? null;
    } catch {
      this.sessionExpired();
      return null;
    }
  }

  /**
   * Revokes the current access token at the Gateway first (so it stops working at once,
   * not when it expires), then ends the Keycloak session, which lands back on /login.
   */
  async logout(): Promise<void> {
    const token = this.getToken();
    if (token) {
      await firstValueFrom(
        this.http.post(`${API_BASE_URL}/auth/logout`, {}, { headers: { Authorization: `Bearer ${token}` } }),
      ).catch(() => undefined);
    }
    this.currentUserSignal.set(null);
    this.permissionsSignal.set([]);
    await this.keycloak.logout({ redirectUri: `${location.origin}/login` });
  }

  /** The session ended (24-hour cap reached, signed out elsewhere, or a token rejected):
   *  drop it locally and go to the login page, which explains why. */
  sessionExpired(): void {
    if (!this.currentUserSignal() && !this.keycloak.token) return;
    this.currentUserSignal.set(null);
    this.permissionsSignal.set([]);
    this.keycloak.clearToken();
    // At app start the router hasn't navigated yet and would override this redirect;
    // authGuard picks up expiredNotice instead.
    if (this.router.navigated) {
      this.router.navigate(['/login'], { queryParams: { expired: 1 } });
    } else {
      this.expiredNotice = true;
    }
  }

  /** True once after an expiry-driven sign-out, so authGuard can explain the redirect. */
  consumeExpiredNotice(): boolean {
    const notice = this.expiredNotice;
    this.expiredNotice = false;
    return notice;
  }

  /** Lets Profile push a freshly-updated copy back in after a save, without a round trip. */
  setCurrentUser(user: ProfileResponse): void {
    this.currentUserSignal.set(user);
  }
}
