import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);

  submit(): void {
    if (this.form.invalid) {
      this.errorMessage.set('Enter a valid email address and password.');
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.auth.login({ email: email!, password: password! }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl('/app');
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // Mirrors flow-04-login.mmd's three branches — the backend's message
        // already distinguishes "wrong credentials" from "account inactive/suspended".
        this.errorMessage.set(
          err.error?.message ?? 'Something went wrong signing in. Please try again.',
        );
      },
    });
  }
}
