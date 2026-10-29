import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Adds a fresh Keycloak access token to every API call (refreshing it first if it's
 *  about to expire). Requests that already carry their own Authorization header — logout
 *  sends the token it is revoking — are left alone. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (!req.url.startsWith('/api/') || req.headers.has('Authorization')) {
    return next(req);
  }
  return from(auth.freshToken()).pipe(
    switchMap((token) => next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req)),
    catchError((err: unknown) => {
      // A 401 means the token was rejected — e.g. revoked by a logout in another tab —
      // so sign out here too.
      if (err instanceof HttpErrorResponse && err.status === 401) {
        auth.sessionExpired();
      }
      return throwError(() => err);
    }),
  );
};
