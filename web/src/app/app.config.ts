import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // A page refresh still has a token in localStorage but an empty currentUser
    // signal — resolve it once before the router activates any route, so guards
    // see accurate state on the very first navigation instead of bouncing to
    // /login for a split second. An expired/invalid token just logs out quietly.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      if (!auth.getToken()) {
        return;
      }
      return firstValueFrom(
        auth.restoreSession().pipe(
          catchError(() => {
            auth.logout();
            return of(null);
          }),
        ),
      );
    }),
  ],
};
