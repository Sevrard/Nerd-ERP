import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CGU_SECTIONS, CGV_SECTIONS, LEGAL_EDITOR, LegalSection, TERMS_VERSION } from './legal-content';

type LegalKind = 'cgu' | 'cgv';

/** Pages publiques CGU / CGV (accessibles sans session, liées depuis l'écran de connexion). */
@Component({
  selector: 'app-legal-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen bg-slate-950 px-4 py-10 text-slate-300">
      <article class="mx-auto w-full max-w-3xl">
        <nav class="mb-8 flex flex-wrap items-center gap-3 text-xs">
          <a [routerLink]="auth.isAuthenticated() ? '/dashboard' : '/login'" class="text-slate-400 hover:text-white">
            ← {{ auth.isAuthenticated() ? 'Retour à l’application' : 'Retour à la connexion' }}
          </a>
          <span class="text-slate-700">·</span>
          <a routerLink="/legal/cgu" class="hover:text-white" [class.text-white]="kind() === 'cgu'" [class.font-semibold]="kind() === 'cgu'">CGU</a>
          <a routerLink="/legal/cgv" class="hover:text-white" [class.text-white]="kind() === 'cgv'" [class.font-semibold]="kind() === 'cgv'">CGV</a>
        </nav>

        <header class="mb-8 border-b border-slate-800 pb-6">
          <p class="text-xs font-semibold uppercase tracking-wider text-brand-400">Nerd ERP</p>
          <h1 class="mt-1 text-2xl font-bold text-white">{{ title() }}</h1>
          <p class="mt-2 text-sm text-slate-500">Version {{ version }} · Éditeur : {{ editor.name }}, {{ editor.address }}</p>
        </header>

        <div class="space-y-8">
          @for (s of sections(); track s.title; let i = $index) {
            <section>
              <h2 class="text-base font-semibold text-white">{{ i + 1 }}. {{ s.title }}</h2>
              @for (p of s.paragraphs; track p) {
                <p class="mt-2 text-sm leading-relaxed">{{ p }}</p>
              }
            </section>
          }
        </div>

        <footer class="mt-10 border-t border-slate-800 pt-6 text-xs text-slate-500">
          Contact : <a class="text-slate-400 hover:text-white" [href]="'mailto:' + editor.email">{{ editor.email }}</a>
        </footer>
      </article>
    </div>
  `,
})
export class LegalPageComponent {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly version = TERMS_VERSION;
  readonly editor = LEGAL_EDITOR;
  readonly kind = toSignal(this.route.data.pipe(map((d) => (d['kind'] as LegalKind | undefined) ?? 'cgu')), {
    initialValue: 'cgu' as LegalKind,
  });
  readonly title = computed(() =>
    this.kind() === 'cgv' ? 'Conditions générales de vente' : 'Conditions générales d’utilisation',
  );
  readonly sections = computed<LegalSection[]>(() => (this.kind() === 'cgv' ? CGV_SECTIONS : CGU_SECTIONS));
}
