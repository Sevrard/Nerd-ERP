import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TokenInfo } from '../../shared/models/account.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AccountApiService } from './account-api.service';
import { AuthShellComponent } from './auth-shell.component';
import { passwordMatch } from './password-match.validator';

@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent, AuthShellComponent],
  template: `
    <app-auth-shell title="Nouveau mot de passe" [subtitle]="info() ? 'Compte ' + info()!.email : null">
      @if (loading()) {
        <app-loading-state label="Vérification du lien…" />
      } @else if (linkError()) {
        <app-error-banner [message]="linkError()!" />
        <a routerLink="/forgot-password" class="mt-6 block text-center text-sm text-slate-400 hover:text-white">Demander un nouveau lien</a>
      } @else if (done()) {
        <div class="rounded-lg border border-emerald-500/30 bg-emerald-950/30 px-4 py-3 text-sm text-emerald-200">
          Votre mot de passe a été modifié. Vous pouvez vous connecter.
        </div>
        <a routerLink="/login" class="mt-6 block w-full rounded-lg bg-brand-600 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-500">Se connecter</a>
      } @else {
        @if (error()) {
          <app-error-banner class="mb-4 block" [message]="error()!" />
        }
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="password">Nouveau mot de passe</label>
            <input id="password" type="password" autocomplete="new-password" formControlName="password" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none" />
            <p class="mt-1 text-xs text-slate-500">8 caractères minimum.</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="confirm">Confirmation</label>
            <input id="confirm" type="password" autocomplete="new-password" formControlName="confirm" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none" />
            @if (form.hasError('passwordMismatch') && form.controls.confirm.touched) {
              <p class="mt-1 text-xs text-red-400">Les deux mots de passe ne correspondent pas.</p>
            }
          </div>
          <button type="submit" class="w-full rounded-lg bg-brand-600 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-500 disabled:opacity-50" [disabled]="form.invalid || busy()">
            Enregistrer
          </button>
        </form>
      }
    </app-auth-shell>
  `,
})
export class ResetPasswordPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AccountApiService);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly done = signal(false);
  readonly info = signal<TokenInfo | null>(null);
  readonly linkError = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      confirm: ['', Validators.required],
    },
    { validators: passwordMatch },
  );

  constructor() {
    if (!this.token) {
      this.loading.set(false);
      this.linkError.set('Lien incomplet : ouvrez le lien reçu par e-mail.');
      return;
    }
    this.api
      .tokenInfo(this.token)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (info) => {
          if (info.kind !== 'PASSWORD_RESET') {
            this.linkError.set('Ce lien ne permet pas de réinitialiser un mot de passe.');
            return;
          }
          this.info.set(info);
        },
        error: (err) => this.linkError.set(apiErrorMessage(err, 'Ce lien est invalide ou a expiré.')),
      });
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .resetPassword(this.token, this.form.getRawValue().password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.done.set(true),
        error: (err) => this.error.set(apiErrorMessage(err, 'Réinitialisation impossible')),
      });
  }
}
