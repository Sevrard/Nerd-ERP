import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, startWith, switchMap, catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginBranding } from '../../shared/models/onboarding.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AccountApiService } from './account-api.service';
import { AuthShellComponent } from './auth-shell.component';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent, AuthShellComponent],
  template: `
    <app-auth-shell [title]="branding()?.tenantName ? 'Connexion · ' + branding()!.tenantName : 'Connexion Nerd ERP'" [branding]="branding()">
      @if (error()) {
        <app-error-banner class="mb-4 block" [message]="error()!" />
      }

      @if (loading()) {
        <app-loading-state label="Authentification…" />
      } @else {
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="tenantSlug"
              >Compte</label
            >
            <input
              id="tenantSlug"
              type="text"
              autocomplete="organization"
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
            <div class="mb-1 flex items-center justify-between">
              <label class="block text-xs font-medium uppercase tracking-wide text-slate-500" for="password">Mot de passe</label>
              <a routerLink="/forgot-password" class="text-xs text-slate-400 hover:text-white">Mot de passe oublié ?</a>
            </div>
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
            [style.background-color]="branding()?.brandColor ?? null"
            [disabled]="form.invalid"
          >
            Se connecter
          </button>
        </form>
      }
    </app-auth-shell>
  `,
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Habillage du compte saisi (logo, couleur, accroche) ; `null` = thème Nerd ERP par défaut. */
  readonly branding = signal<LoginBranding | null>(null);

  readonly form = this.fb.nonNullable.group({
    tenantSlug: [this.route.snapshot.queryParamMap.get('tenant') ?? this.auth.getStoredTenantSlug(), Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    // Le branding suit le compte saisi (ou `?tenant=` dans l'URL), sans bloquer le formulaire en cas d'échec.
    this.form.controls.tenantSlug.valueChanges
      .pipe(
        startWith(this.form.controls.tenantSlug.value),
        map((slug) => slug.trim().toLowerCase()),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((slug) => (slug ? this.account.loginBranding(slug).pipe(catchError(() => of(null))) : of(null))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((b) => this.branding.set(b?.tenantSlug ? b : null));
  }

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
