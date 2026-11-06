import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AccountApiService } from '../../features/auth/account-api.service';
import { apiErrorMessage } from '../../shared/utils/api-error.util';

/**
 * Modale bloquante tant que l'utilisateur n'a pas accepté la version courante des CGU
 * (`/auth/me` → `terms.accepted === false`). L'acceptation est tracée côté serveur.
 */
@Component({
  selector: 'app-terms-gate',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (mustAccept()) {
      <div class="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/90 p-4" role="dialog" aria-modal="true" aria-labelledby="terms-title">
        <div class="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
          <p class="text-xs font-semibold uppercase tracking-wider text-brand-400">Nerd ERP</p>
          <h2 id="terms-title" class="mt-1 text-lg font-semibold text-white">
            @if (terms()?.acceptedVersion) {
              Les conditions d'utilisation ont évolué
            } @else {
              Bienvenue ! Avant de commencer
            }
          </h2>
          <p class="mt-3 text-sm text-slate-300">
            Pour utiliser l'application, merci de prendre connaissance et d'accepter les
            <a routerLink="/legal/cgu" target="_blank" class="text-brand-400 underline-offset-2 hover:underline">conditions générales d'utilisation</a>
            (version {{ terms()?.currentVersion }}). Les
            <a routerLink="/legal/cgv" target="_blank" class="text-brand-400 underline-offset-2 hover:underline">conditions de vente</a>
            sont consultables à tout moment.
          </p>
          <label class="mt-4 flex cursor-pointer items-start gap-3 text-sm text-slate-200">
            <input type="checkbox" class="mt-0.5" [checked]="checked()" (change)="checked.set($any($event.target).checked)" />
            J'ai lu et j'accepte les conditions générales d'utilisation.
          </label>
          @if (error()) {
            <p class="mt-3 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{{ error() }}</p>
          }
          <div class="mt-5 flex items-center justify-between gap-3">
            <button type="button" class="text-sm text-slate-400 hover:text-white" (click)="auth.logout()">Se déconnecter</button>
            <button
              type="button"
              class="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-500 disabled:opacity-50"
              [disabled]="!checked() || busy()"
              (click)="accept()"
            >
              {{ busy() ? 'Enregistrement…' : 'Accepter et continuer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class TermsGateComponent {
  readonly auth = inject(AuthService);
  private readonly account = inject(AccountApiService);

  readonly terms = computed(() => this.auth.profile()?.terms ?? null);
  readonly mustAccept = computed(() => this.auth.profileLoaded() && this.terms()?.accepted === false);
  readonly checked = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  accept(): void {
    if (this.busy() || !this.checked()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.account.acceptTerms().subscribe({
      next: (status) => {
        this.busy.set(false);
        this.auth.updateTerms(status);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible'));
      },
    });
  }
}
