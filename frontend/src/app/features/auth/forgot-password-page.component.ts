import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AccountApiService } from './account-api.service';
import { AuthShellComponent } from './auth-shell.component';

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, AuthShellComponent],
  template: `
    <app-auth-shell
      title="Mot de passe oublié"
      subtitle="Indiquez votre adresse e-mail : si un compte existe, vous recevrez un lien valable une heure."
    >
      @if (sent()) {
        <div class="rounded-lg border border-emerald-500/30 bg-emerald-950/30 px-4 py-3 text-sm text-emerald-200">
          Si l’adresse <span class="font-medium">{{ form.controls.email.value }}</span> correspond à un compte, un e-mail
          vient de lui être envoyé. Pensez à vérifier vos courriers indésirables.
        </div>
        <a routerLink="/login" class="mt-6 block text-center text-sm text-slate-400 hover:text-white">← Retour à la connexion</a>
      } @else {
        @if (error()) {
          <app-error-banner class="mb-4 block" [message]="error()!" />
        }
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="email">E-mail</label>
            <input
              id="email"
              type="email"
              autocomplete="username"
              formControlName="email"
              class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none"
            />
          </div>
          <button
            type="submit"
            class="w-full rounded-lg bg-brand-600 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-500 disabled:opacity-50"
            [disabled]="form.invalid || busy()"
          >
            Envoyer le lien
          </button>
          <a routerLink="/login" class="block text-center text-sm text-slate-400 hover:text-white">← Retour à la connexion</a>
        </form>
      }
    </app-auth-shell>
  `,
})
export class ForgotPasswordPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AccountApiService);

  readonly busy = signal(false);
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .forgotPassword(this.form.getRawValue().email.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (err) => this.error.set(apiErrorMessage(err, 'Envoi impossible pour le moment')),
      });
  }
}
