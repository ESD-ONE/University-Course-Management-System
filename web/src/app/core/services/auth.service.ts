import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, KEYCLOAK_URL } from '../config/api.config';
import { ProfileResponse } from '../models/user.model';
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

  private readonly keycloak = new Keycloak({ url: KEYCLOAK_URL, realm: 'ucms', clientId: 'ucms-web' });
  private expiredNotice = false;

  constructor(
    private http: HttpClient,
    private userService: UserService,
    private router: Router,
  ) {}

  /**
   * Called once at app start: completes the redirect back from Keycloak's login page, or
   * silently picks up an existing Keycloak session (so a page refresh doesn't sign the
   * user out), then loads their profile before any route guard runs.
   */
  async init(): Promise<void> {
    this.keycloak.onTokenExpired = () => {
      this.keycloak.updateToken(30).catch(() => this.sessionExpired());
    };
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
      checkLoginIframe: false,
    });
    if (authenticated) {
      this.currentUserSignal.set(await firstValueFrom(this.userService.me()));
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
    await this.keycloak.logout({ redirectUri: `${location.origin}/login` });
  }

  /** The session ended (24-hour cap reached, signed out elsewhere, or a token rejected):
   *  drop it locally and go to the login page, which explains why. */
  sessionExpired(): void {
    if (!this.currentUserSignal() && !this.keycloak.token) return;
    this.currentUserSignal.set(null);
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
