import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TokenInfo } from '../../shared/models/account.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';
import { AccountApiService } from './account-api.service';
import { AuthShellComponent } from './auth-shell.component';
import { passwordMatch } from './password-match.validator';

/** Activation d'un compte invité : choix du nom et du mot de passe, puis connexion automatique. */
@Component({
  selector: 'app-activate-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ErrorBannerComponent, LoadingStateComponent, AuthShellComponent],
  template: `
    <app-auth-shell
      title="Bienvenue sur Nerd ERP"
      [subtitle]="info() ? 'Vous rejoignez « ' + info()!.tenantName + ' » avec l’identifiant ' + info()!.email : null"
    >
      @if (loading()) {
        <app-loading-state label="Vérification de l’invitation…" />
      } @else if (linkError()) {
        <app-error-banner [message]="linkError()!" />
        <p class="mt-4 text-sm text-slate-400">Demandez à l’administrateur de votre compte de vous renvoyer une invitation.</p>
        <a routerLink="/login" class="mt-6 block text-center text-sm text-slate-400 hover:text-white">← Connexion</a>
      } @else {
        @if (error()) {
          <app-error-banner class="mb-4 block" [message]="error()!" />
        }
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="displayName">Votre nom</label>
            <input id="displayName" type="text" autocomplete="name" formControlName="displayName" class="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-brand-500 focus:outline-none" />
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500" for="password">Mot de passe</label>
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
            Activer mon compte
          </button>
        </form>
      }
    </app-auth-shell>
  `,
})
export class ActivatePageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AccountApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly info = signal<TokenInfo | null>(null);
  readonly linkError = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      displayName: ['', [Validators.required, Validators.maxLength(255)]],
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
          if (info.kind !== 'INVITATION') {
            this.linkError.set('Ce lien ne correspond pas à une invitation.');
            return;
          }
          this.info.set(info);
          // Le nom pré-rempli par l'invitant est modifiable ; l'adresse e-mail sert de repli.
          this.form.controls.displayName.setValue(info.displayName !== info.email ? info.displayName : '');
        },
        error: (err) => this.linkError.set(apiErrorMessage(err, 'Cette invitation est invalide ou a expiré.')),
      });
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api
      .activate({ token: this.token, displayName: v.displayName.trim(), password: v.password })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (session) => {
          this.auth.openSession(session);
          void this.router.navigate(['/dashboard']);
        },
        error: (err) => this.error.set(apiErrorMessage(err, 'Activation impossible')),
      });
  }
}
