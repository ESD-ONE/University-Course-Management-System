import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * The app's front door. Credentials are entered on Keycloak's own login page, not here —
 * this page explains where the user is and why (e.g. their session expired) and sends
 * them there.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);

  readonly notice = signal<string | null>(
    inject(ActivatedRoute).snapshot.queryParamMap.has('expired')
      ? 'Your session has expired. Please sign in again.'
      : null,
  );
  readonly redirecting = signal(false);

  signIn(): void {
    this.redirecting.set(true);
    this.auth.login().catch(() => {
      this.redirecting.set(false);
      this.notice.set('The sign-in service is unavailable right now. Please try again shortly.');
    });
  }
}
