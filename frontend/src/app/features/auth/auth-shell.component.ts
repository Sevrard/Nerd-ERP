import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LoginBranding } from '../../shared/models/onboarding.model';

/**
 * Cadre commun des écrans hors session (connexion, mot de passe oublié, activation).
 * Peut être habillé aux couleurs du compte client (`branding`) : logo, couleur d'accent, accroche.
 */
@Component({
  selector: 'app-auth-shell',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4 py-8">
      <div
        class="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-8 shadow-xl"
        [style.border-top]="branding()?.brandColor ? '3px solid ' + branding()!.brandColor : null"
      >
        @if (branding()?.logoUrl) {
          <img
            [src]="branding()!.logoUrl"
            [alt]="branding()!.tenantName ?? 'Logo'"
            class="mb-5 max-h-14 max-w-[200px] object-contain"
            referrerpolicy="no-referrer"
          />
        }
        <div class="mb-6 flex items-center gap-2">
          <span
            class="inline-block h-3 w-3 rounded-full bg-brand-500"
            [style.background-color]="branding()?.brandColor ?? null"
          ></span>
          <h1 class="text-xl font-bold text-white">{{ title() }}</h1>
        </div>
        @if (subtitle() || branding()?.tagline) {
          <p class="-mt-3 mb-6 text-sm text-slate-400">{{ subtitle() ?? branding()?.tagline }}</p>
        }
        <ng-content />
      </div>
      <p class="mt-6 flex items-center gap-3 text-xs text-slate-500">
        <a routerLink="/legal/cgu" class="hover:text-slate-300">Conditions d'utilisation</a>
        <span class="text-slate-700">·</span>
        <a routerLink="/legal/cgv" class="hover:text-slate-300">Conditions de vente</a>
      </p>
    </div>
  `,
})
export class AuthShellComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly branding = input<LoginBranding | null>(null);
}
