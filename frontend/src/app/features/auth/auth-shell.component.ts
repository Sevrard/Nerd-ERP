import { Component, input } from '@angular/core';

/** Cadre commun des écrans hors session (connexion, mot de passe oublié, activation). */
@Component({
  selector: 'app-auth-shell',
  standalone: true,
  template: `
    <div class="flex min-h-screen items-center justify-center bg-slate-950 px-4">
      <div class="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-8 shadow-xl">
        <div class="mb-6 flex items-center gap-2">
          <span class="inline-block h-3 w-3 rounded-full bg-brand-500"></span>
          <h1 class="text-xl font-bold text-white">{{ title() }}</h1>
        </div>
        @if (subtitle()) {
          <p class="-mt-3 mb-6 text-sm text-slate-400">{{ subtitle() }}</p>
        }
        <ng-content />
      </div>
    </div>
  `,
})
export class AuthShellComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}
