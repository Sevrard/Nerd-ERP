import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, ErrorBannerComponent, LoadingStateComponent],
  template: `
    <div class="flex min-h-screen items-center justify-center bg-slate-950 px-4">
      <div class="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-8 shadow-xl">
        <div class="mb-8 flex items-center gap-2">
          <span class="inline-block h-3 w-3 rounded-full bg-brand-500"></span>
          <h1 class="text-xl font-bold text-white">Connexion Nerd ERP</h1>
        </div>

        @if (error()) {
          <app-error-banner class="mb-4 block" [message]="error()!" />
        }

        @if (loading()) {
          <app-loading-state label="Authentification…" />
        } @else {
          <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="tenantSlug"
                >Tenant</label
              >
              <input
                id="tenantSlug"
                type="text"
                formControlName="tenantSlug"
                class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="email"
                >E-mail</label
              >
              <input
                id="email"
                type="email"
                autocomplete="username"
                formControlName="email"
                class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="password"
                >Mot de passe</label
              >
              <input
                id="password"
                type="password"
                autocomplete="current-password"
                formControlName="password"
                class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none"
              />
            </div>
            <button
              type="submit"
              class="w-full rounded-lg bg-brand-600 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-500 disabled:opacity-50"
              [disabled]="form.invalid"
            >
              Se connecter
            </button>
          </form>
        }
      </div>
    </div>
  `,
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    tenantSlug: [this.auth.getStoredTenantSlug(), Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Identifiants invalides'));
      },
      complete: () => this.loading.set(false),
    });
  }
}
