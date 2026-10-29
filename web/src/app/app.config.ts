import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Finish a redirect back from Keycloak's login page, or silently pick up an existing
    // Keycloak session, before the router activates any route — so guards see accurate
    // state on the very first navigation. If Keycloak can't be reached the app still
    // starts, signed out; the login page's button retries.
    provideAppInitializer(() =>
      inject(AuthService)
        .init()
        .catch((err) => console.warn('Sign-in could not be restored', err)),
    ),
  ],
};
