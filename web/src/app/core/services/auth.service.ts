import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { API_BASE_URL } from '../config/api.config';
import { LoginRequest, LoginResponse } from '../models/auth.model';
import { ProfileResponse } from '../models/user.model';
import { UserService } from './user.service';

const TOKEN_KEY = 'ucms_token';

/**
 * Holds the signed-in user's full profile (fetched from GET /users/me right after
 * login, since LoginResponse only carries token/role/name — not id/email/status/
 * notification preferences, which the Users screen, route guards, and Profile page need).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<ProfileResponse | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  constructor(
    private http: HttpClient,
    private userService: UserService,
  ) {}

  login(request: LoginRequest): Observable<ProfileResponse> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => localStorage.setItem(TOKEN_KEY, response.token)),
      switchMap(() => this.userService.me()),
      tap((user) => this.currentUserSignal.set(user)),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.currentUserSignal.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** Lets Profile push a freshly-updated copy back in after a save, without a round trip. */
  setCurrentUser(user: ProfileResponse): void {
    this.currentUserSignal.set(user);
  }

  /** Called once at app start so a page refresh doesn't drop a still-valid session. */
  restoreSession(): Observable<ProfileResponse> {
    return this.userService.me().pipe(tap((user) => this.currentUserSignal.set(user)));
  }
}
