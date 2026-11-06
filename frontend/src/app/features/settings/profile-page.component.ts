import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AccountApiService } from '../auth/account-api.service';
import { passwordMatch } from '../auth/password-match.validator';
import { Profile } from '../../shared/models/account.model';
import { roleLabel } from '../../shared/models/auth.model';
import { ErrorBannerComponent } from '../../shared/ui/error-banner.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

/** Profil de l'utilisateur connecté : nom affiché et mot de passe. */
@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, LoadingStateComponent, ErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="max-w-2xl space-y-8">
      <div>
        <h1 class="text-2xl font-bold text-white">Mon profil</h1>
        @if (profile(); as p) {
          <p class="mt-1 text-sm text-slate-400">
            {{ p.email }} · {{ roleLabel(auth.profile()?.role) }}
            @if (p.lastLoginAt) {
              · dernière connexion le {{ p.lastLoginAt | date: 'dd/MM/yyyy à HH:mm' }}
            }
          </p>
        }
      </div>

      @if (error()) {
        <app-error-banner [message]="error()!" />
      }
      @if (loading()) {
        <app-loading-state />
      } @else if (profile()) {
        <form class="rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="nameForm" (ngSubmit)="saveName()">
          <h2 class="text-lg font-semibold text-white">Identité</h2>
          <label class="mt-4 block text-sm text-slate-300">
            Nom affiché
            <input formControlName="displayName" required maxlength="255" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
          </label>
          <p class="mt-2 text-xs text-slate-500">L’adresse e-mail sert d’identifiant ; demandez à un administrateur pour la changer.</p>
          @if (nameError()) {
            <app-error-banner class="mt-4 block" [message]="nameError()!" />
          }
          @if (nameSaved()) {
            <p class="mt-4 text-sm text-emerald-300">Nom mis à jour.</p>
          }
          <button type="submit" class="mt-5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="busy() || nameForm.invalid || nameForm.pristine">
            Enregistrer
          </button>
        </form>

        <form class="rounded-xl border border-slate-800 bg-slate-900/50 p-6" [formGroup]="passwordForm" (ngSubmit)="savePassword()">
          <h2 class="text-lg font-semibold text-white">Mot de passe</h2>
          <div class="mt-4 grid gap-4 sm:grid-cols-3">
            <label class="block text-sm text-slate-300 sm:col-span-3">
              Mot de passe actuel
              <input type="password" formControlName="current" autocomplete="current-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300 sm:col-span-2">
              Nouveau mot de passe <span class="text-slate-500">(8 caractères min.)</span>
              <input type="password" formControlName="password" autocomplete="new-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
            <label class="block text-sm text-slate-300">
              Confirmation
              <input type="password" formControlName="confirm" autocomplete="new-password" class="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" />
            </label>
          </div>
          @if (passwordForm.hasError('passwordMismatch') && passwordForm.controls.confirm.touched) {
            <p class="mt-2 text-xs text-red-400">Les deux mots de passe ne correspondent pas.</p>
          }
          @if (passwordError()) {
            <app-error-banner class="mt-4 block" [message]="passwordError()!" />
          }
          @if (passwordSaved()) {
            <p class="mt-4 text-sm text-emerald-300">Mot de passe modifié.</p>
          }
          <button type="submit" class="mt-5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500 disabled:opacity-50" [disabled]="busy() || passwordForm.invalid">
            Changer le mot de passe
          </button>
        </form>
      }
    </div>
  `,
})
export class ProfilePageComponent {
  private readonly api = inject(AccountApiService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly profile = signal<Profile | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly nameError = signal<string | null>(null);
  readonly nameSaved = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordSaved = signal(false);

  readonly roleLabel = roleLabel;

  readonly nameForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(255)]],
  });

  readonly passwordForm = this.fb.nonNullable.group(
    {
      current: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      confirm: ['', Validators.required],
    },
    { validators: passwordMatch },
  );

  constructor() {
    this.loading.set(true);
    this.api
      .getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.profile.set(p);
          this.nameForm.reset({ displayName: p.displayName });
        },
        error: (err) => this.error.set(apiErrorMessage(err, 'Impossible de charger le profil')),
      });
  }

  saveName(): void {
    if (this.nameForm.invalid || this.busy()) {
      return;
    }
    const displayName = this.nameForm.getRawValue().displayName.trim();
    this.busy.set(true);
    this.nameError.set(null);
    this.nameSaved.set(false);
    this.api
      .updateProfile(displayName)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (p) => {
          this.profile.set(p);
          this.nameForm.reset({ displayName: p.displayName });
          this.auth.updateDisplayName(p.displayName);
          this.nameSaved.set(true);
        },
        error: (err) => this.nameError.set(apiErrorMessage(err, 'Enregistrement impossible')),
      });
  }

  savePassword(): void {
    if (this.passwordForm.invalid || this.busy()) {
      return;
    }
    const v = this.passwordForm.getRawValue();
    this.busy.set(true);
    this.passwordError.set(null);
    this.passwordSaved.set(false);
    this.api
      .changePassword(v.current, v.password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.passwordForm.reset({ current: '', password: '', confirm: '' });
          this.passwordSaved.set(true);
        },
        error: (err) => this.passwordError.set(apiErrorMessage(err, 'Modification impossible')),
      });
  }
}
