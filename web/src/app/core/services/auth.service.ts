import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { API_BASE_URL } from '../config/api.config';
import { LoginRequest, LoginResponse } from '../models/auth.model';
import { UserResponse } from '../models/user.model';
import { UserService } from './user.service';

const TOKEN_KEY = 'ucms_token';

/**
 * Holds the signed-in user's full profile (fetched from GET /users/me right after
 * login, since LoginResponse only carries token/role/name — not id/email/status,
 * which the Users screen and route guards need).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<UserResponse | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  constructor(
    private http: HttpClient,
    private userService: UserService,
  ) {}

  login(request: LoginRequest): Observable<UserResponse> {
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

  /** Called once at app start so a page refresh doesn't drop a still-valid session. */
  restoreSession(): Observable<UserResponse> {
    return this.userService.me().pipe(tap((user) => this.currentUserSignal.set(user)));
  }
}
