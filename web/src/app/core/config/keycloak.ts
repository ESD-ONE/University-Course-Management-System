import { InjectionToken, Provider } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_URL } from './api.config';

/**
 * The single keycloak-js instance, provided through DI rather than constructed inside
 * AuthService. Keeping it behind a token means a test can substitute a fake Keycloak
 * (keycloak-js otherwise reaches for the real browser/network at construction), while
 * production still gets exactly the instance {@link keycloakFactory} builds.
 */
export const KEYCLOAK = new InjectionToken<Keycloak>('KEYCLOAK');

/** Builds the app's keycloak-js client — the realm/client the ucms-web SPA signs in with. */
export function keycloakFactory(): Keycloak {
  return new Keycloak({ url: KEYCLOAK_URL, realm: 'ucms', clientId: 'ucms-web' });
}

export const keycloakProvider: Provider = { provide: KEYCLOAK, useFactory: keycloakFactory };
